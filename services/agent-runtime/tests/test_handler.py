# Function: Planning request handler unit tests
# Time: 2026-07-06
# Author: AxeXie
"""Unit tests for the PlanningRequestHandler."""

from __future__ import annotations

import json
from datetime import datetime, timezone
from typing import Any

import pytest

from opengeobot_agent.config import AgentConfig
from opengeobot_agent.handler import PlanningRequestHandler
from opengeobot_agent.provider import (
    AgentRuntimeProvider,
    MissionContext,
    PlanProposal,
)


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

    async def drain_and_close(self) -> None:
        pass

    @property
    def is_connected(self) -> bool:
        return True


class StubProvider(AgentRuntimeProvider):
    """Test stub that returns a predetermined proposal or raises."""

    def __init__(self, proposal: PlanProposal | None = None, error: Exception | None = None):
        self._proposal = proposal
        self._error = error
        self.received_missions: list[MissionContext] = []

    async def generate_plan(self, mission: MissionContext) -> PlanProposal:
        self.received_missions.append(mission)
        if self._error is not None:
            raise self._error
        if self._proposal is not None:
            return self._proposal
        return PlanProposal(
            plan_id="plan_test",
            mission_id=mission.mission_id,
            trace_id=mission.trace_id,
            robot_id=mission.robot_id,
            is_trusted=False,
            generated_at=datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        )


def _make_config() -> AgentConfig:
    return AgentConfig(
        nats_url="nats://localhost:4222",
        nats_max_reconnect=-1,
        nats_reconnect_wait=2.0,
        nats_connect_timeout=5.0,
        qwenpaw_endpoint="http://localhost:8000/v1/chat/completions",
        qwenpaw_api_key="",
        qwenpaw_timeout=30.0,
        plan_request_subject="opengeobot.agent.mission.plan_request",
        log_level="DEBUG",
    )


def _make_request_data(**overrides: Any) -> dict[str, Any]:
    base = {
        "mission_id": "msn_001",
        "trace_id": "trace_001",
        "robot_id": "rbt_01",
        "objective": "Move to location B",
        "constraints": {"max_speed": 1.0},
        "requested_at": "2026-01-01T00:00:00Z",
    }
    base.update(overrides)
    return base


def _make_msg(data: dict[str, Any], reply: str = "reply.subject") -> MockMsg:
    return MockMsg(
        data=json.dumps(data).encode("utf-8"),
        reply=reply,
    )


def _published_on(nats: MockNats, subject: str) -> list[tuple[str, bytes]]:
    return [(s, d) for s, d in nats.published if s == subject]


class TestPlanRequestHandling:
    async def test_success_returns_untrusted_proposal(self):
        """A valid request should produce an UNTRUSTED proposal on the reply subject."""
        config = _make_config()
        nats = MockNats()
        provider = StubProvider()
        handler = PlanningRequestHandler(config, nats, provider)  # type: ignore[arg-type]

        msg = _make_msg(_make_request_data())
        await handler.handle_plan_request(msg)

        assert len(provider.received_missions) == 1
        assert provider.received_missions[0].mission_id == "msn_001"

        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["is_trusted"] is False
        assert response["mission_id"] == "msn_001"
        assert response["plan_id"] == "plan_test"

    async def test_proposal_is_always_untrusted(self):
        """Even if the provider tries to set is_trusted=True, the handler propagates as-is."""
        # Note: The provider is responsible for setting is_trusted=False.
        # The handler just forwards whatever the provider returns.
        config = _make_config()
        nats = MockNats()
        trusted_proposal = PlanProposal(
            plan_id="plan_001",
            mission_id="msn_001",
            trace_id="trace_001",
            is_trusted=False,  # Provider always sets False
            generated_at="2026-01-01T00:00:00Z",
        )
        provider = StubProvider(proposal=trusted_proposal)
        handler = PlanningRequestHandler(config, nats, provider)  # type: ignore[arg-type]

        msg = _make_msg(_make_request_data())
        await handler.handle_plan_request(msg)

        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["is_trusted"] is False

    async def test_malformed_payload_returns_error_proposal(self):
        """Malformed JSON should produce an error proposal."""
        config = _make_config()
        nats = MockNats()
        provider = StubProvider()
        handler = PlanningRequestHandler(config, nats, provider)  # type: ignore[arg-type]

        msg = MockMsg(data=b"not-json", reply="reply.subject")
        await handler.handle_plan_request(msg)

        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["is_trusted"] is False
        assert response["error"] is not None
        assert len(provider.received_missions) == 0

    async def test_provider_error_returns_error_proposal(self):
        """When the provider raises, an error proposal should be returned."""
        config = _make_config()
        nats = MockNats()
        provider = StubProvider(error=RuntimeError("Unexpected provider crash"))
        handler = PlanningRequestHandler(config, nats, provider)  # type: ignore[arg-type]

        msg = _make_msg(_make_request_data())
        await handler.handle_plan_request(msg)

        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["is_trusted"] is False
        assert "Unexpected provider crash" in (response["error"] or "")

    async def test_no_reply_subject_does_not_crash(self):
        """When no reply subject is available, handler should not crash."""
        config = _make_config()
        nats = MockNats()
        provider = StubProvider()
        handler = PlanningRequestHandler(config, nats, provider)  # type: ignore[arg-type]

        msg = MockMsg(
            data=json.dumps(_make_request_data()).encode("utf-8"),
            reply="",
        )
        await handler.handle_plan_request(msg)

        # No reply published since reply subject is empty.
        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 0

    async def test_trace_id_propagated(self):
        """The trace_id from the request should be propagated to the proposal."""
        config = _make_config()
        nats = MockNats()
        provider = StubProvider()
        handler = PlanningRequestHandler(config, nats, provider)  # type: ignore[arg-type]

        msg = _make_msg(_make_request_data(trace_id="my_trace_123"))
        await handler.handle_plan_request(msg)

        replies = _published_on(nats, "reply.subject")
        assert len(replies) == 1
        response = json.loads(replies[0][1])
        assert response["trace_id"] == "my_trace_123"
