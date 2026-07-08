# Function: ROS1 adapter main handler unit tests
# Time: 2026-07-08
# Author: AxeXie
"""Unit tests for the ROS1 adapter request handler and protocol selection (F-ADAPTER-002)."""

from __future__ import annotations

import json
from typing import Any
from unittest.mock import AsyncMock, MagicMock

import pytest

from opengeobot_ros1.adapter import TranslationError
from opengeobot_ros1.config import Ros1Config
from opengeobot_ros1.custom_adapter import CustomAdapter
from opengeobot_ros1.main import Ros1Adapter, TranslateRequest, TranslateResponse, _select_protocol_handler
from opengeobot_ros1.unitree_adapter import UnitreeAdapter


class MockMsg:
    """Mimics nats.aio.msg.Msg for testing."""

    def __init__(self, data: bytes, reply: str = "") -> None:
        self.data = data
        self.reply = reply


class MockNats:
    """Records publishes for verification."""

    def __init__(self) -> None:
        self.published: list[tuple[str, bytes]] = []

    async def publish(self, subject: str, data: bytes) -> None:
        self.published.append((subject, data))

    async def subscribe(self, subject: str, cb: Any = None, queue: str | None = None) -> Any:
        return MagicMock()

    async def drain(self) -> None:
        pass


def _make_config(**overrides: Any) -> Ros1Config:
    defaults: dict[str, Any] = {
        "adapter_id": "adp_test",
        "robot_id": "rbt_test",
        "protocol_type": "UNITREE",
        "version": "0.1.0",
        "nats_url": "nats://localhost:4222",
        "nats_max_reconnect": -1,
        "nats_reconnect_wait": 2.0,
        "nats_connect_timeout": 5.0,
        "ros_master_uri": "http://localhost:11311",
        "node_name": "opengeobot_ros1",
        "log_level": "DEBUG",
    }
    defaults.update(overrides)
    return Ros1Config(**defaults)


def _make_adapter(
    config: Ros1Config | None = None,
) -> tuple[Ros1Adapter, MockNats]:
    config = config or _make_config()
    adapter = Ros1Adapter(config)
    nats = MockNats()
    adapter._nc = nats  # type: ignore[assignment]
    return adapter, nats


def _make_request_data(**overrides: Any) -> dict[str, Any]:
    base: dict[str, Any] = {
        "request_id": "req_001",
        "trace_id": "trace_001",
        "adapter_id": "adp_test",
        "skill_id": "stand_up",
        "params": {"duration": 3.0},
        "requested_at": "2026-01-01T00:00:00Z",
    }
    base.update(overrides)
    return base


def _make_msg(data: dict[str, Any], reply: str = "reply.subject") -> MockMsg:
    return MockMsg(data=json.dumps(data).encode("utf-8"), reply=reply)


def _replies_on(nats: MockNats, subject: str) -> list[bytes]:
    return [d for s, d in nats.published if s == subject]


class TestSelectProtocolHandler:
    def test_unitree_returns_unitree_adapter(self) -> None:
        handler = _select_protocol_handler("UNITREE")
        assert isinstance(handler, UnitreeAdapter)
        assert handler.protocol_type == "UNITREE"

    def test_unitree_lowercase(self) -> None:
        handler = _select_protocol_handler("unitree")
        assert isinstance(handler, UnitreeAdapter)

    def test_custom_returns_custom_adapter(self) -> None:
        handler = _select_protocol_handler("CUSTOM")
        assert isinstance(handler, CustomAdapter)
        assert handler.protocol_type == "CUSTOM"

    def test_ros1_returns_custom_adapter(self) -> None:
        """ROS1 native uses CustomAdapter until the ROS1 Jazzy contract is pinned."""
        handler = _select_protocol_handler("ROS1")
        assert isinstance(handler, CustomAdapter)

    def test_ros1_lowercase(self) -> None:
        handler = _select_protocol_handler("ros1")
        assert isinstance(handler, CustomAdapter)

    def test_unsupported_protocol_raises(self) -> None:
        with pytest.raises(ValueError, match="Unsupported protocol_type"):
            _select_protocol_handler("UNKNOWN")


class TestProtocolTypeProperty:
    def test_unitree_protocol_type(self) -> None:
        adapter, _ = _make_adapter(_make_config(protocol_type="UNITREE"))
        assert adapter.protocol_type == "UNITREE"

    def test_custom_protocol_type(self) -> None:
        adapter, _ = _make_adapter(_make_config(protocol_type="CUSTOM"))
        assert adapter.protocol_type == "CUSTOM"

    def test_ros1_protocol_type(self) -> None:
        adapter, _ = _make_adapter(_make_config(protocol_type="ROS1"))
        assert adapter.protocol_type == "CUSTOM"


class TestHandleRequestSuccess:
    async def test_stand_up_translation(self) -> None:
        adapter, nats = _make_adapter(_make_config(protocol_type="UNITREE"))
        msg = _make_msg(_make_request_data(skill_id="stand_up", params={"duration": 3.0}))
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is True
        assert response["request_id"] == "req_001"
        assert response["trace_id"] == "trace_001"
        assert response["adapter_id"] == "adp_test"
        assert response["skill_id"] == "stand_up"
        assert response["translated_command"]["topic"] == "/standUpCmd"
        assert response["translated_command"]["duration"] == 3.0
        assert response["translated_at"] != ""
        assert response["error"] is None

    async def test_move_forward_translation(self) -> None:
        adapter, nats = _make_adapter(_make_config(protocol_type="UNITREE"))
        msg = _make_msg(
            _make_request_data(skill_id="move_forward", params={"speed": 0.5, "distance": 2.0})
        )
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is True
        assert response["translated_command"]["topic"] == "/walkCmd"
        assert response["translated_command"]["linear"]["x"] == 0.5

    async def test_custom_protocol_translation(self) -> None:
        adapter, nats = _make_adapter(_make_config(protocol_type="CUSTOM"))
        msg = _make_msg(_make_request_data(skill_id="capture_image", params={"resolution": "640x480"}))
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is True
        assert response["translated_command"]["command"] == "capture_image"
        assert response["translated_command"]["topic"] == "/custom/capture_image"
        assert response["translated_command"]["seq"] == 1


class TestHandleRequestTranslationError:
    async def test_unsupported_skill_returns_error(self) -> None:
        adapter, nats = _make_adapter(_make_config(protocol_type="UNITREE"))
        msg = _make_msg(_make_request_data(skill_id="nonexistent"))
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is False
        assert "Unsupported skill_id" in response["error"]
        assert response["translated_command"] == {}

    async def test_capture_image_unsupported_in_unitree(self) -> None:
        adapter, nats = _make_adapter(_make_config(protocol_type="UNITREE"))
        msg = _make_msg(_make_request_data(skill_id="capture_image"))
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is False


class TestHandleRequestMalformed:
    async def test_invalid_json_returns_error(self) -> None:
        adapter, nats = _make_adapter()
        msg = MockMsg(data=b"not-json", reply="reply.subject")
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is False
        assert response["error"] != ""

    async def test_missing_required_fields_returns_error(self) -> None:
        adapter, nats = _make_adapter()
        msg = MockMsg(data=json.dumps({"request_id": "x"}).encode("utf-8"), reply="reply.subject")
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is False


class TestNoReplySubject:
    async def test_none_reply_does_not_publish(self) -> None:
        adapter, nats = _make_adapter()
        msg = MockMsg(
            data=json.dumps(_make_request_data()).encode("utf-8"),
            reply=None,  # type: ignore[arg-type]
        )
        await adapter._handle_request(msg)
        assert len(nats.published) == 0


class TestHandlerCrash:
    async def test_handler_crash_returns_error(self) -> None:
        """A crash in the handler should not kill the adapter."""

        class CrashingHandler:
            protocol_type = "CRASH"

            def translate(self, skill_id: str, params: dict[str, Any]) -> dict[str, Any]:
                raise RuntimeError("handler crashed")

        adapter, nats = _make_adapter()
        adapter._handler = CrashingHandler()  # type: ignore[assignment]

        msg = _make_msg(_make_request_data())
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is False
        assert "Translation crashed" in response["error"]


class TestStartStop:
    async def test_start_connects_and_subscribes(self, monkeypatch: pytest.MonkeyPatch) -> None:
        mock_nc = MockNats()
        mock_connect = AsyncMock(return_value=mock_nc)
        monkeypatch.setattr("nats.connect", mock_connect)

        config = _make_config()
        adapter = Ros1Adapter(config)
        await adapter.start()

        mock_connect.assert_called_once()
        assert mock_connect.call_args.kwargs["servers"] == "nats://localhost:4222"
        assert mock_connect.call_args.kwargs["name"] == "ros1-adapter-adp_test"

    async def test_stop_drains_and_clears_nc(self, monkeypatch: pytest.MonkeyPatch) -> None:
        mock_nc = MockNats()
        mock_connect = AsyncMock(return_value=mock_nc)
        monkeypatch.setattr("nats.connect", mock_connect)

        config = _make_config()
        adapter = Ros1Adapter(config)
        await adapter.start()
        await adapter.stop()

        assert adapter._nc is None

    async def test_stop_without_start_does_not_crash(self) -> None:
        adapter = Ros1Adapter(_make_config())
        await adapter.stop()


class TestExtraFieldsIgnored:
    async def test_extra_fields_in_request_ignored(self) -> None:
        """The request model_config has extra='ignore'."""
        adapter, nats = _make_adapter()
        data = _make_request_data()
        data["extra_field"] = "should_be_ignored"
        msg = _make_msg(data)
        await adapter._handle_request(msg)

        replies = _replies_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0])
        assert response["success"] is True
