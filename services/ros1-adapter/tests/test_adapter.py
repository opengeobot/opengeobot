# Function: Protocol adapter interface unit tests
# Time: 2026-07-08
# Author: AxeXie
"""Unit tests for the protocol adapter interface and TranslationError (F-ADAPTER-002)."""

from __future__ import annotations

import pytest

from opengeobot_ros1.adapter import ProtocolAdapter, TranslationError
from opengeobot_ros1.custom_adapter import CustomAdapter
from opengeobot_ros1.unitree_adapter import UnitreeAdapter


class TestProtocolAdapterProtocol:
    def test_unitree_adapter_satisfies_protocol(self) -> None:
        adapter = UnitreeAdapter()
        assert isinstance(adapter, ProtocolAdapter)

    def test_custom_adapter_satisfies_protocol(self) -> None:
        adapter = CustomAdapter()
        assert isinstance(adapter, ProtocolAdapter)

    def test_protocol_type_attribute_exists(self) -> None:
        assert UnitreeAdapter().protocol_type == "UNITREE"
        assert CustomAdapter().protocol_type == "CUSTOM"


class TestTranslationError:
    def test_is_exception(self) -> None:
        err = TranslationError("test error")
        assert isinstance(err, Exception)
        assert str(err) == "test error"

    def test_can_be_raised(self) -> None:
        with pytest.raises(TranslationError, match="translation failed"):
            raise TranslationError("translation failed")
