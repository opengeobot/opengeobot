-- Function: Add replan_count column to mission table for dynamic replanning
-- Time: 2026-07-16
-- Author: AxeXie

ALTER TABLE mission.mission ADD COLUMN IF NOT EXISTS replan_count INTEGER NOT NULL DEFAULT 0;
