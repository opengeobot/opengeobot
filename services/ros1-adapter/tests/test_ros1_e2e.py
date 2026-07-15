# Function: ROS1 adapter end-to-end integration tests
# Time: 2026-07-15
# Author: AxeXie
"""End-to-end integration tests for the ROS1 adapter (F-ADAPTER-002).

Tests the full chain: NATS request -> ros1-adapter -> ROS1 topic message.

Two layers:
1. Unit-level E2E with mocked rospy (runs in CI) - verifies request/response
   chain, trace_id propagation, and Twist message publishing via the
   ROS1_NATIVE protocol handler.
2. Real ROS1 integration tests (marked ``@pytest.mark.integration``) - require a
   ROS1 host with rospy installed; skipped in normal CI via
   ``-m "not integration"``.
"""

from __future__ import annotations

import json
from typing import Any

import pytest

from opengeobot_ros1.config import Ros1Config
from opengeobot_ros1.main import Ros1Adapter, TranslateResponse


# ------------------------------------------------------------------
# Fake rospy classes - stand in for rospy, Twist, Publisher.
# ------------------------------------------------------------------


class FakeVector3:
    """Mimics geometry_msgs.msg.Vector3."""

    def __init__(self) -> None:
        self.x = 0.0
        self.y = 0.0
        self.z = 0.0


class FakeTwist:
    """Mimics geometry_msgs.msg.Twist."""

    def __init__(self) -> None:
        self.linear = FakeVector3()
        self.angular = FakeVector3()


class FakeRospyPublisher:
    """Records published Twist messages for verification."""

    def __init__(self, topic: str, msg_type: Any) -> None:
        self.topic = topic
        self.msg_type = msg_type
        self.published: list[Any] = []

    def publish(self, msg: Any) -> None:
        self.published.append(msg)


class FakeRospyCore:
    """Fake rospy.core module."""

    @staticmethod
    def is_initialized() -> bool:
        return False


class FakeRospy:
    """Fake rospy module for testing."""

    def __init__(self) -> None:
        self.core = FakeRospyCore()
        self.init_node_called = False
        self.publisher: FakeRospyPublisher | None = None

    def init_node(self, name: str, anonymous: bool = False) -> None:
        self.init_node_called = True

    def Publisher(
        self, topic: str, msg_type: Any, queue_size: int = 10
    ) -> FakeRospyPublisher:
        self.publisher = FakeRospyPublisher(topic, msg_type)
        return self.publisher


# ------------------------------------------------------------------
# Mock NATS classes - mimic nats.aio.msg.Msg and NatsClient.
# ------------------------------------------------------------------


class MockMsg:
    """Mimics nats.aio.msg.Msg for testing, with JetStream ack support."""

    def __init__(self, data: bytes, reply: str = "") -> None:
        self.data = data
        self.reply = reply
        self.acked = False

    async def ack(self) -> None:
        self.acked = True


class MockNats:
    """Records publishes for verification."""

    def __init__(self) -> None:
        self.published: list[tuple[str, bytes]] = []

    async def publish(self, subject: str, data: bytes) -> None:
        self.published.append((subject, data))

    async def drain(self) -> None:
        pass


# ------------------------------------------------------------------
# Helpers.
# ------------------------------------------------------------------


def _make_config(**overrides: Any) -> Ros1Config:
    defaults: dict[str, Any] = {
        "adapter_id": "adp_test",
        "robot_id": "rbt_test",
        "protocol_type": "ROS1_NATIVE",
        "version": "0.1.0",
        "nats_url": "nats://localhost:4222",
        "nats_max_reconnect": -1,
        "nats_reconnect_wait": 2.0,
        "nats_connect_timeout": 5.0,
        "ros_master_uri": "http://localhost:11311",
        "node_name": "opengeobot_ros1_test",
        "log_level": "DEBUG",
    }
    defaults.update(overrides)
    return Ros1Config(**defaults)


def _make_request_data(**overrides: Any) -> dict[str, Any]:
    base: dict[str, Any] = {
        "request_id": "req_e2e_001",
        "trace_id": "trace_e2e_001",
        "adapter_id": "adp_test",
        "skill_id": "move_forward",
        "params": {"speed": 0.5, "duration": 2.0},
        "requested_at": "2026-01-01T00:00:00Z",
    }
    base.update(overrides)
    return base


def _make_msg(data: dict[str, Any], reply: str = "reply.subject") -> MockMsg:
    return MockMsg(data=json.dumps(data).encode("utf-8"), reply=reply)


def _replies_on(nats: MockNats, subject: str) -> list[bytes]:
    return [d for s, d in nats.published if s == subject]


def _parse_response(raw: bytes) -> dict[str, Any]:
    return json.loads(raw)


# ------------------------------------------------------------------
# Unit-level E2E: NATS request -> ros1-adapter -> mocked rospy publish.
# These tests run in CI (not marked as integration).
# ------------------------------------------------------------------


class TestRos1E2EMockedRospy:
    """Unit-level E2E: NATS request -> ros1-adapter -> FakeRospy publish -> response.

    Patches ``_ROSPY_AVAILABLE``, ``rospy`` and ``Twist`` in the
    ``ros1_native_adapter`` module so the ``Ros1NativeAdapter`` publishes real
    FakeTwist messages to a ``FakeRospyPublisher``, allowing verification of
    the full chain without a ROS1 installation.
    """

    @pytest.fixture(autouse=True)
    def mock_rospy(self, monkeypatch: pytest.MonkeyPatch) -> None:
        """Patch rospy-related symbols so the native adapter publishes via FakeRospy."""
        import opengeobot_ros1.ros1_native_adapter as native_mod

        self._fake_rospy = FakeRospy()
        monkeypatch.setattr(native_mod, "_ROSPY_AVAILABLE", True)
        monkeypatch.setattr(native_mod, "rospy", self._fake_rospy)
        monkeypatch.setattr(native_mod, "Twist", FakeTwist)

    @staticmethod
    def _make_mocked_adapter(
        config: Ros1Config | None = None,
    ) -> tuple[Ros1Adapter, MockNats, FakeRospyPublisher | None]:
        """Create an adapter with mocked rospy and MockNats."""
        config = config or _make_config()
        adapter = Ros1Adapter(config)
        nats = MockNats()
        adapter._nc = nats  # type: ignore[assignment]
        publisher = getattr(adapter._handler, "_publisher", None)
        return adapter, nats, publisher

    # ------------------------------------------------------------------
    # trace_id propagation
    # ------------------------------------------------------------------

    async def test_trace_id_propagated_through_response(self) -> None:
        """The response trace_id must match the request trace_id."""
        adapter, nats, _ = self._make_mocked_adapter()
        msg = _make_msg(_make_request_data(trace_id="trace_abc_123"))
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = _parse_response(replies[0])
        assert response["trace_id"] == "trace_abc_123"

    async def test_request_id_propagated_through_response(self) -> None:
        """The response request_id must match the request request_id."""
        adapter, nats, _ = self._make_mocked_adapter()
        msg = _make_msg(_make_request_data(request_id="req_xyz_456"))
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = _parse_response(replies[0])
        assert response["request_id"] == "req_xyz_456"

    async def test_response_is_valid_translate_response(self) -> None:
        """The response must parse into a TranslateResponse."""
        adapter, nats, _ = self._make_mocked_adapter()
        msg = _make_msg(_make_request_data())
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = TranslateResponse.model_validate_json(replies[0])
        assert response.success is True
        assert response.skill_id == "move_forward"
        assert response.trace_id == "trace_e2e_001"

    # ------------------------------------------------------------------
    # move_forward -> Twist publish
    # ------------------------------------------------------------------

    async def test_move_forward_publishes_twist(self) -> None:
        """move_forward must publish a Twist to /turtle1/cmd_vel."""
        adapter, _, publisher = self._make_mocked_adapter()
        assert publisher is not None, "FakeRospyPublisher should be created"

        msg = _make_msg(
            _make_request_data(
                skill_id="move_forward", params={"speed": 0.5, "duration": 2.0}
            )
        )
        await adapter._handle_request(msg)

        assert len(publisher.published) == 1

    async def test_move_forward_twist_has_correct_linear_x(self) -> None:
        """The published Twist must have linear.x equal to the requested speed."""
        adapter, _, publisher = self._make_mocked_adapter()
        speed = 0.8
        msg = _make_msg(
            _make_request_data(
                skill_id="move_forward", params={"speed": speed, "duration": 1.0}
            )
        )
        await adapter._handle_request(msg)

        twist = publisher.published[0]
        assert twist.linear.x == pytest.approx(speed)
        assert twist.linear.y == 0.0
        assert twist.linear.z == 0.0

    async def test_move_forward_twist_has_zero_angular(self) -> None:
        """move_forward must publish a Twist with all angular components zero."""
        adapter, _, publisher = self._make_mocked_adapter()
        msg = _make_msg(
            _make_request_data(skill_id="move_forward", params={"speed": 0.5})
        )
        await adapter._handle_request(msg)

        twist = publisher.published[0]
        assert twist.angular.x == 0.0
        assert twist.angular.y == 0.0
        assert twist.angular.z == 0.0

    async def test_move_forward_translated_command(self) -> None:
        """move_forward response translated_command must have correct topic and values."""
        adapter, nats, _ = self._make_mocked_adapter()
        msg = _make_msg(
            _make_request_data(
                skill_id="move_forward", params={"speed": 0.5, "duration": 2.0}
            )
        )
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        response = _parse_response(replies[0])
        assert response["success"] is True
        cmd = response["translated_command"]
        assert cmd["topic"] == "/turtle1/cmd_vel"
        assert cmd["type"] == "geometry_msgs/Twist"
        assert cmd["linear"]["x"] == 0.5
        assert cmd["linear"]["y"] == 0.0
        assert cmd["linear"]["z"] == 0.0
        assert cmd["angular"]["x"] == 0.0
        assert cmd["angular"]["y"] == 0.0
        assert cmd["angular"]["z"] == 0.0
        assert cmd["duration"] == 2.0
        assert cmd["published"] is True

    # ------------------------------------------------------------------
    # emergency_stop -> zero Twist
    # ------------------------------------------------------------------

    async def test_emergency_stop_publishes_zero_twist(self) -> None:
        """emergency_stop must publish a zero Twist to /turtle1/cmd_vel."""
        adapter, _, publisher = self._make_mocked_adapter()

        msg = _make_msg(
            _make_request_data(skill_id="emergency_stop", params={})
        )
        await adapter._handle_request(msg)

        assert len(publisher.published) == 1
        twist = publisher.published[0]
        assert twist.linear.x == 0.0
        assert twist.linear.y == 0.0
        assert twist.linear.z == 0.0
        assert twist.angular.x == 0.0
        assert twist.angular.y == 0.0
        assert twist.angular.z == 0.0

    async def test_emergency_stop_response_trace_id(self) -> None:
        """emergency_stop response must carry the correct trace_id."""
        adapter, nats, _ = self._make_mocked_adapter()
        msg = _make_msg(
            _make_request_data(
                skill_id="emergency_stop", params={}, trace_id="trace_estop_001"
            )
        )
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = _parse_response(replies[0])
        assert response["success"] is True
        assert response["trace_id"] == "trace_estop_001"
        assert response["skill_id"] == "emergency_stop"

    async def test_emergency_stop_translated_command(self) -> None:
        """emergency_stop translated_command must have emergency_stop flag."""
        adapter, nats, _ = self._make_mocked_adapter()
        msg = _make_msg(
            _make_request_data(skill_id="emergency_stop", params={})
        )
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        response = _parse_response(replies[0])
        assert response["success"] is True
        cmd = response["translated_command"]
        assert cmd["topic"] == "/turtle1/cmd_vel"
        assert cmd["type"] == "geometry_msgs/Twist"
        assert cmd["linear"]["x"] == 0.0
        assert cmd["emergency_stop"] is True
        assert cmd["published"] is True

    # ------------------------------------------------------------------
    # stop -> zero Twist
    # ------------------------------------------------------------------

    async def test_stop_publishes_zero_twist(self) -> None:
        """stop must also publish a zero Twist to /turtle1/cmd_vel."""
        adapter, _, publisher = self._make_mocked_adapter()
        msg = _make_msg(_make_request_data(skill_id="stop", params={}))
        await adapter._handle_request(msg)

        assert len(publisher.published) == 1
        twist = publisher.published[0]
        assert twist.linear.x == 0.0
        assert twist.angular.z == 0.0

    async def test_stop_translated_command(self) -> None:
        """stop translated_command must have zero linear."""
        adapter, nats, _ = self._make_mocked_adapter()
        msg = _make_msg(_make_request_data(skill_id="stop", params={}))
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        response = _parse_response(replies[0])
        assert response["success"] is True
        cmd = response["translated_command"]
        assert cmd["linear"]["x"] == 0.0

    # ------------------------------------------------------------------
    # Full chain: request -> ack + response
    # ------------------------------------------------------------------

    async def test_jetstream_msg_acked_after_execution(self) -> None:
        """A JetStream message must be acked after the translation completes."""
        adapter, nats, _ = self._make_mocked_adapter()
        msg = _make_msg(_make_request_data())
        await adapter._handle_request(msg)

        assert msg.acked is True
        assert len(_replies_on(nats, "reply.subject")) == 1

    async def test_full_chain_move_forward_e2e(self) -> None:
        """Full E2E: request -> adapter -> translate -> rospy publish -> response -> ack."""
        adapter, nats, publisher = self._make_mocked_adapter()
        msg = _make_msg(
            _make_request_data(
                request_id="req_full_001",
                trace_id="trace_full_001",
                skill_id="move_forward",
                params={"speed": 0.5, "duration": 2.0},
            )
        )
        await adapter._handle_request(msg)

        # 1. Response published on reply subject.
        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = _parse_response(replies[0])
        assert response["success"] is True
        assert response["request_id"] == "req_full_001"
        assert response["trace_id"] == "trace_full_001"
        assert response["skill_id"] == "move_forward"
        assert response["adapter_id"] == "adp_test"
        assert response["translated_at"] != ""
        assert response["error"] is None

        # 2. Twist published via FakeRospyPublisher.
        assert len(publisher.published) == 1
        twist = publisher.published[0]
        assert twist.linear.x == pytest.approx(0.5)

        # 3. JetStream message acked.
        assert msg.acked is True

    async def test_full_chain_emergency_stop_e2e(self) -> None:
        """Full E2E: emergency_stop request -> zero Twist -> response -> ack."""
        adapter, nats, publisher = self._make_mocked_adapter()
        msg = _make_msg(
            _make_request_data(
                request_id="req_estop_001",
                trace_id="trace_estop_001",
                skill_id="emergency_stop",
                params={},
            )
        )
        await adapter._handle_request(msg)

        # 1. Response.
        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = _parse_response(replies[0])
        assert response["success"] is True
        assert response["trace_id"] == "trace_estop_001"

        # 2. Zero Twist published.
        assert len(publisher.published) == 1
        twist = publisher.published[0]
        assert twist.linear.x == 0.0
        assert twist.angular.z == 0.0

        # 3. Acked.
        assert msg.acked is True

    # ------------------------------------------------------------------
    # Error propagation in the chain
    # ------------------------------------------------------------------

    async def test_unknown_skill_does_not_publish_twist(self) -> None:
        """An unknown skill must not publish any Twist."""
        adapter, nats, publisher = self._make_mocked_adapter()
        msg = _make_msg(_make_request_data(skill_id="nonexistent"))
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = _parse_response(replies[0])
        assert response["success"] is False
        assert len(publisher.published) == 0

    async def test_trace_id_propagated_on_error(self) -> None:
        """trace_id must be propagated even when translation fails."""
        adapter, nats, _ = self._make_mocked_adapter()
        msg = _make_msg(
            _make_request_data(
                skill_id="nonexistent", trace_id="trace_error_001"
            )
        )
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = _parse_response(replies[0])
        assert response["success"] is False
        assert response["trace_id"] == "trace_error_001"

    async def test_malformed_request_trace_id_empty(self) -> None:
        """A malformed request results in a response with empty trace_id."""
        adapter, nats, _ = self._make_mocked_adapter()
        msg = MockMsg(data=b"not-json", reply="reply.subject")
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = _parse_response(replies[0])
        assert response["success"] is False
        assert response["trace_id"] == ""

    # ------------------------------------------------------------------
    # Multiple requests / trace_id isolation
    # ------------------------------------------------------------------

    async def test_multiple_requests_isolate_trace_ids(self) -> None:
        """Each request must carry its own trace_id through the chain."""
        adapter, nats, _ = self._make_mocked_adapter()

        for i in range(3):
            msg = _make_msg(
                _make_request_data(
                    request_id=f"req_{i}",
                    trace_id=f"trace_{i}",
                    skill_id="move_forward",
                    params={"speed": 0.1 * (i + 1), "duration": 1.0},
                )
            )
            await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 3
        for i, raw in enumerate(replies):
            response = _parse_response(raw)
            assert response["trace_id"] == f"trace_{i}"
            assert response["request_id"] == f"req_{i}"


# ------------------------------------------------------------------
# Real ROS1 integration tests - require rospy installed.
# Marked @pytest.mark.integration so they are skipped in CI.
# ------------------------------------------------------------------


class TestRos1E2ERealRospy:
    """Integration tests that require a real ROS1 host with rospy installed.

    These tests are skipped in normal CI via ``-m "not integration"``.
    They exercise the real rospy publish path against a live ROS1 node.
    """

    @classmethod
    def setup_class(cls) -> None:
        """Skip all tests in this class if rospy is not available."""
        pytest.importorskip("rospy")
        pytest.importorskip("geometry_msgs.msg")
        import rospy  # noqa: F401  # ensure rospy is importable

    async def test_real_move_forward_translation(self) -> None:
        """Ros1NativeAdapter.translate returns correct dict for move_forward."""
        from opengeobot_ros1.ros1_native_adapter import Ros1NativeAdapter

        adapter = Ros1NativeAdapter(
            ros_master_uri="http://localhost:11311",
            node_name="test_opengeobot_ros1_e2e",
        )
        result = adapter.translate("move_forward", {"speed": 0.5, "duration": 2.0})
        assert result["topic"] == "/turtle1/cmd_vel"
        assert result["type"] == "geometry_msgs/Twist"
        assert result["linear"]["x"] == 0.5

    async def test_real_emergency_stop_translation(self) -> None:
        """Ros1NativeAdapter.translate returns zero Twist for emergency_stop."""
        from opengeobot_ros1.ros1_native_adapter import Ros1NativeAdapter

        adapter = Ros1NativeAdapter(
            ros_master_uri="http://localhost:11311",
            node_name="test_opengeobot_ros1_e2e",
        )
        result = adapter.translate("emergency_stop", {})
        assert result["topic"] == "/turtle1/cmd_vel"
        assert result["linear"]["x"] == 0.0
        assert result["emergency_stop"] is True

    async def test_real_stop_translation(self) -> None:
        """Ros1NativeAdapter.translate returns zero Twist for stop."""
        from opengeobot_ros1.ros1_native_adapter import Ros1NativeAdapter

        adapter = Ros1NativeAdapter(
            ros_master_uri="http://localhost:11311",
            node_name="test_opengeobot_ros1_e2e",
        )
        result = adapter.translate("stop", {})
        assert result["topic"] == "/turtle1/cmd_vel"
        assert result["linear"]["x"] == 0.0
