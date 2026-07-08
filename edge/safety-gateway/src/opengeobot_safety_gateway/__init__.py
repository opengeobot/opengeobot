# Function: OpenGeoBot Safety Gateway package
# Time: 2026-07-06
# Author: AxeXie
"""OpenGeoBot Edge Safety Gateway runtime.

F-SAFETY-001 edge-side safety enforcement. Implements the SM-SAFETY-001 state
machine (NORMAL → EMERGENCY_STOPPED → RESETTING → NORMAL) with latching
emergency stop that does NOT depend on cloud or network connectivity. All skill
execution requests are intercepted and validated against restricted zones, speed
limits and collision risk before being forwarded to the local skill executor.
"""

__version__ = "0.1.0"
