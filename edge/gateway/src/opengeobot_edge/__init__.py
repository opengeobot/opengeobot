# Function: OpenGeoBot Edge Gateway package
# Time: 2026-07-05
# Author: AxeXie
"""OpenGeoBot Edge Gateway runtime.

F-EDGE-001 (identity, connection) and F-EDGE-002 (local execution, offline
cache, reconciliation). The gateway bridges the cloud control plane and the
local skill executor (sim-adapter for M2) over NATS. Physical actions are never
invoked directly here; they are forwarded as registered skill executions to the
local executor and remain subject to the Safety Gateway latch.
"""

__version__ = "0.1.0"
