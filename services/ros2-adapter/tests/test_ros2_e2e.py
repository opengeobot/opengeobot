# Function: ROS2 adapter end-to-end integration tests
# Time: 2026-07-15
# Author: AxeXie
"""End-to-end integration tests for the ROS2 adapter (F-ADAPTER-003).

Tests the full chain: NATS request -> ros2-adapter -> ROS2 topic message.

Two layers:
1. Unit-level E2E with mocked rclpy (runs in CI) - verifies request/response
   chain, trace_id propagation, and Twist message publishing to FakeNode.
2. Real ROS2 integration tests (marked ``@pytest.mark.integration``) - require a
   ROS2 Jazzy host with rclpy installed; skipped in normal CI via
   ``-m "not integration"``.
"""

from __future__ import annotations

import json
from typing import Any

import pytest

from opengeobot_ros2.adapter import Ros2Adapter, SkillExecutionResponse
from opengeobot_ros2.config import DEFAULT_JETSTREAM_STREAM, Ros2Config


# ------------------------------------------------------------------
# Fake rclpy classes - stand in for rclpy Node, Twist, Publisher.
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


class FakePublisher:
    """Records published messages for verification."""

    def __init__(self, topic: str, msg_type: Any) -> None:
        self.topic = topic
        self.msg_type = msg_type
        self.published: list[Any] = []

    def publish(self, msg: Any) -> None:
        self.published.append(msg)


class FakeNode:
    """Fake rclpy Node that records created publishers."""

    def __init__(self, name: str) -> None:
        self.name = name
        self._publishers: dict[str, FakePublisher] = {}

    def create_publisher(
        self, msg_type: Any, topic: str, qos_depth: int
    ) -> FakePublisher:
        if topic not in self._publishers:
            self._publishers[topic] = FakePublisher(topic, msg_type)
        return self._publishers[topic]


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


def _make_config(**overrides: Any) -> Ros2Config:
    defaults: dict[str, Any] = {
        "robot_id": "rbt_test",
        "nats_url": "nats://localhost:4222",
        "nats_max_reconnect": -1,
        "nats_reconnect_wait": 2.0,
        "nats_connect_timeout": 5.0,
        "dds_domain_id": 42,
        "log_level": "DEBUG",
        "jetstream_stream_name": DEFAULT_JETSTREAM_STREAM,
    }
    defaults.update(overrides)
    return Ros2Config(**defaults)


def _make_request_data(**overrides: Any) -> dict[str, Any]:
    base: dict[str, Any] = {
        "request_id": "skreq_e2e_001",
        "trace_id": "trace_e2e_001",
        "robot_id": "rbt_test",
        "skill_id": "move_forward",
        "params": {"distance": 2.0, "speed": 1.0},
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
# Unit-level E2E: NATS request -> ros2-adapter -> mocked rclpy publish.
# These tests run in CI (not marked as integration).
# ------------------------------------------------------------------


class TestRos2E2EMockedRclpy:
    """Unit-level E2E: NATS request -> ros2-adapter -> FakeNode publish -> response.

    Patches ``RCLPY_AVAILABLE``, ``Node`` and ``Twist`` so skills publish real
    FakeTwist messages to a FakeNode, allowing verification of the full chain
    without a ROS2 installation.
    """

    @pytest.fixture(autouse=True)
    def mock_rclpy(self, monkeypatch: pytest.MonkeyPatch) -> None:
        """Patch rclpy-related symbols so skills publish to FakeNode."""
        import opengeobot_ros2.adapter as adapter_mod
        import opengeobot_ros2.skills.base as base_mod

        monkeypatch.setattr(base_mod, "RCLPY_AVAILABLE", True)
        monkeypatch.setattr(base_mod, "Node", FakeNode)
        monkeypatch.setattr(base_mod, "Twist", FakeTwist)
        monkeypatch.setattr(adapter_mod, "RCLPY_AVAILABLE", True)
        monkeypatch.setattr(adapter_mod, "Node", FakeNode)

    @staticmethod
    def _make_mocked_adapter(
        config: Ros2Config | None = None,
    ) -> tuple[Ros2Adapter, MockNats, FakeNode]:
        """Create an adapter with mocked rclpy (FakeNode) and MockNats."""
        config = config or _make_config()
        adapter = Ros2Adapter(config)
        nats = MockNats()
        adapter._nc = nats  # type: ignore[assignment]
        fake_node = adapter._node
        assert isinstance(fake_node, FakeNode), "Expected FakeNode after mock_rclpy"
        return adapter, nats, fake_node

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

    async def test_response_is_valid_skill_execution_response(self) -> None:
        """The response must parse into a SkillExecutionResponse."""
        adapter, nats, _ = self._make_mocked_adapter()
        msg = _make_msg(_make_request_data())
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = SkillExecutionResponse.model_validate_json(replies[0])
        assert response.success is True
        assert response.skill_id == "move_forward"
        assert response.trace_id == "trace_e2e_001"

    # ------------------------------------------------------------------
    # move_forward -> Twist publish
    # ------------------------------------------------------------------

    async def test_move_forward_publishes_twist_to_cmd_vel(self) -> None:
        """move_forward must publish a Twist to /turtle1/cmd_vel."""
        adapter, _, fake_node = self._make_mocked_adapter()
        msg = _make_msg(
            _make_request_data(skill_id="move_forward", params={"distance": 2.0, "speed": 1.0})
        )
        await adapter._handle_request(msg)

        assert "/turtle1/cmd_vel" in fake_node._publishers
        pub = fake_node._publishers["/turtle1/cmd_vel"]
        assert len(pub.published) == 1

    async def test_move_forward_twist_has_correct_linear_x(self) -> None:
        """The published Twist must have linear.x equal to the requested speed."""
        adapter, _, fake_node = self._make_mocked_adapter()
        speed = 0.8
        msg = _make_msg(
            _make_request_data(
                skill_id="move_forward", params={"distance": 4.0, "speed": speed}
            )
        )
        await adapter._handle_request(msg)

        pub = fake_node._publishers["/turtle1/cmd_vel"]
        twist = pub.published[0]
        assert twist.linear.x == pytest.approx(speed)
        assert twist.linear.y == 0.0
        assert twist.linear.z == 0.0

    async def test_move_forward_twist_has_zero_angular(self) -> None:
        """move_forward must publish a Twist with angular.z = 0."""
        adapter, _, fake_node = self._make_mocked_adapter()
        msg = _make_msg(
            _make_request_data(skill_id="move_forward", params={"distance": 2.0, "speed": 1.0})
        )
        await adapter._handle_request(msg)

        pub = fake_node._publishers["/turtle1/cmd_vel"]
        twist = pub.published[0]
        assert twist.angular.x == 0.0
        assert twist.angular.y == 0.0
        assert twist.angular.z == 0.0

    async def test_move_forward_response_output(self) -> None:
        """move_forward response output must include distance and duration."""
        adapter, nats, _ = self._make_mocked_adapter()
        msg = _make_msg(
            _make_request_data(skill_id="move_forward", params={"distance": 4.0, "speed": 1.0})
        )
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        response = _parse_response(replies[0])
        assert response["success"] is True
        assert response["output"]["distance"] == 4.0
        assert response["output"]["duration"] == 4.0

    # ------------------------------------------------------------------
    # emergency_stop -> zero Twist
    # ------------------------------------------------------------------

    async def test_emergency_stop_publishes_zero_twist(self) -> None:
        """emergency_stop must publish a zero Twist to /turtle1/cmd_vel."""
        adapter, _, fake_node = self._make_mocked_adapter()
        msg = _make_msg(
            _make_request_data(skill_id="emergency_stop", params={})
        )
        await adapter._handle_request(msg)

        assert "/turtle1/cmd_vel" in fake_node._publishers
        pub = fake_node._publishers["/turtle1/cmd_vel"]
        assert len(pub.published) == 1
        twist = pub.published[0]
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

    async def test_emergency_stop_response_output(self) -> None:
        """emergency_stop response output must include stopped_missions."""
        adapter, nats, _ = self._make_mocked_adapter()
        msg = _make_msg(
            _make_request_data(skill_id="emergency_stop", params={})
        )
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        response = _parse_response(replies[0])
        assert response["success"] is True
        assert response["output"]["stopped_missions"] >= 1

    # ------------------------------------------------------------------
    # stop -> zero Twist
    # ------------------------------------------------------------------

    async def test_stop_publishes_zero_twist(self) -> None:
        """stop must also publish a zero Twist to /turtle1/cmd_vel."""
        adapter, _, fake_node = self._make_mocked_adapter()
        msg = _make_msg(_make_request_data(skill_id="stop", params={}))
        await adapter._handle_request(msg)

        assert "/turtle1/cmd_vel" in fake_node._publishers
        pub = fake_node._publishers["/turtle1/cmd_vel"]
        assert len(pub.published) == 1
        twist = pub.published[0]
        assert twist.linear.x == 0.0
        assert twist.angular.z == 0.0

    # ------------------------------------------------------------------
    # Full chain: request -> ack + response
    # ------------------------------------------------------------------

    async def test_jetstream_msg_acked_after_execution(self) -> None:
        """A JetStream message must be acked after the skill executes."""
        adapter, nats, _ = self._make_mocked_adapter()
        msg = _make_msg(_make_request_data())
        await adapter._handle_request(msg)

        assert msg.acked is True
        assert len(_replies_on(nats, "reply.subject")) == 1

    async def test_full_chain_move_forward_e2e(self) -> None:
        """Full E2E: request -> adapter -> skill -> FakeNode publish -> response -> ack."""
        adapter, nats, fake_node = self._make_mocked_adapter()
        msg = _make_msg(
            _make_request_data(
                request_id="req_full_001",
                trace_id="trace_full_001",
                skill_id="move_forward",
                params={"distance": 3.0, "speed": 0.5},
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
        assert response["output"]["distance"] == 3.0
        assert response["output"]["duration"] == 6.0
        assert response["started_at"] != ""
        assert response["completed_at"] != ""

        # 2. Twist published to /turtle1/cmd_vel.
        pub = fake_node._publishers["/turtle1/cmd_vel"]
        assert len(pub.published) == 1
        twist = pub.published[0]
        assert twist.linear.x == pytest.approx(0.5)

        # 3. JetStream message acked.
        assert msg.acked is True

    async def test_full_chain_emergency_stop_e2e(self) -> None:
        """Full E2E: emergency_stop request -> zero Twist -> response -> ack."""
        adapter, nats, fake_node = self._make_mocked_adapter()
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
        pub = fake_node._publishers["/turtle1/cmd_vel"]
        assert len(pub.published) == 1
        twist = pub.published[0]
        assert twist.linear.x == 0.0
        assert twist.angular.z == 0.0

        # 3. Acked.
        assert msg.acked is True

    # ------------------------------------------------------------------
    # Error propagation in the chain
    # ------------------------------------------------------------------

    async def test_unknown_skill_does_not_publish_twist(self) -> None:
        """An unknown skill must not publish any Twist."""
        adapter, nats, fake_node = self._make_mocked_adapter()
        msg = _make_msg(_make_request_data(skill_id="nonexistent"))
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = _parse_response(replies[0])
        assert response["success"] is False
        assert "/turtle1/cmd_vel" not in fake_node._publishers

    async def test_trace_id_propagated_on_error(self) -> None:
        """trace_id must be propagated even when the skill fails."""
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
                    params={"distance": 1.0, "speed": 1.0},
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
# Real ROS2 integration tests - require rclpy installed.
# Marked @pytest.mark.integration so they are skipped in CI.
# ------------------------------------------------------------------


class TestRos2E2ERealRclpy:
    """Integration tests that require a real ROS2 Jazzy host with rclpy.

    These tests are skipped in normal CI via ``-m "not integration"``.
    They exercise the real rclpy publish path against a live ROS2 node.
    """

    @classmethod
    def setup_class(cls) -> None:
        """Skip all tests in this class if rclpy is not available."""
        pytest.importorskip("rclpy")
        pytest.importorskip("geometry_msgs.msg")
        import rclpy  # noqa: F401  # ensure rclpy is importable

    @pytest.fixture(autouse=True)
    def rclpy_node(self):
        """Create and shutdown a real rclpy node for each test."""
        import rclpy
        from rclpy.node import Node

        rclpy.init()
        node = Node("test_opengeobot_ros2_e2e")
        yield node
        node.destroy_node()
        rclpy.shutdown()

    async def test_real_move_forward_publishes_twist(self, rclpy_node) -> None:
        """move_forward publishes a Twist with linear.x > 0 to /turtle1/cmd_vel."""
        from opengeobot_ros2.skills.move_forward import MoveForwardSkill
        from opengeobot_ros2.skills.base import SkillContext

        skill = MoveForwardSkill(node=rclpy_node)
        ctx = SkillContext()
        result = await skill.execute({"distance": 1.0, "speed": 0.5}, ctx)
        assert result.success is True
        assert result.output["distance"] == 1.0

    async def test_real_emergency_stop_publishes_zero_twist(self, rclpy_node) -> None:
        """emergency_stop publishes a zero Twist to /turtle1/cmd_vel."""
        from opengeobot_ros2.skills.emergency_stop import EmergencyStopSkill
        from opengeobot_ros2.skills.base import SkillContext

        skill = EmergencyStopSkill(node=rclpy_node)
        ctx = SkillContext()
        result = await skill.execute({}, ctx)
        assert result.success is True
        assert result.output["stopped_missions"] >= 0

    async def test_real_adapter_with_rclpy_node(self, rclpy_node) -> None:
        """The Ros2Adapter creates a real node when rclpy is available."""
        from opengeobot_ros2.skills.base import RCLPY_AVAILABLE

        # This test only runs when rclpy is truly available.
        assert RCLPY_AVAILABLE is True

        config = _make_config()
        adapter = Ros2Adapter(config)
        assert adapter._node is not None
        assert adapter._skills["move_forward"]._node is not None
