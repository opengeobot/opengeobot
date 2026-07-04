-- Function: Create domain schemas for modular monolith
-- Time: 2026-07-03
-- Author: AxeXie

-- Create schemas for each domain module
CREATE SCHEMA IF NOT EXISTS platform_iam;
CREATE SCHEMA IF NOT EXISTS platform_governance;
CREATE SCHEMA IF NOT EXISTS robot_registry;
CREATE SCHEMA IF NOT EXISTS skill_registry;
CREATE SCHEMA IF NOT EXISTS mission;
CREATE SCHEMA IF NOT EXISTS fleet;
CREATE SCHEMA IF NOT EXISTS policy;
CREATE SCHEMA IF NOT EXISTS trace;
CREATE SCHEMA IF NOT EXISTS memory;
CREATE SCHEMA IF NOT EXISTS map_scene;
CREATE SCHEMA IF NOT EXISTS media;
CREATE SCHEMA IF NOT EXISTS ops;

-- Enable pgvector extension if available
CREATE EXTENSION IF NOT EXISTS vector;
