# Function: ROS1 protocol adapter package init (F-ADAPTER-002)
# Time: 2026-07-05
# Author: AxeXie
"""OpenGeoBot ROS1 protocol adapter.

Translates platform skill commands into ROS1/Unitree/custom robot protocol
messages over NATS. The adapter never directly drives motors or joints —
translations are proposals validated by the edge Safety Gateway.
"""
