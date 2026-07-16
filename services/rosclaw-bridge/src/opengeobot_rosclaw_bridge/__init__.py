# Function: ROSClaw NATS Bridge package init
# Time: 2026-07-16
# Author: AxeXie
"""OpenGeoBot ROSClaw NATS Bridge.

Bridges the OpenGeoBot edge NATS pipeline to the ROSClaw Edge Runtime for
real robot hardware control. Subscribes to the same skill execution subject
as the sim-adapter and dispatches requests through ROSClaw's SkillExecutor.
"""

__version__ = "0.1.0"
