-- Function: Create media tables for F-MEDIA-001 media object management
-- Time: 2026-07-05
-- Author: AxeXie

-- Media object table (metadata for files stored in MinIO/S3)
CREATE TABLE media.media_object (
    id BIGSERIAL PRIMARY KEY,
    media_id VARCHAR(64) NOT NULL,
    file_name VARCHAR(512) NOT NULL,
    file_path VARCHAR(1024) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(256),
    media_type VARCHAR(32) NOT NULL,
    robot_id VARCHAR(64),
    mission_id VARCHAR(64),
    uploaded_by VARCHAR(64),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ
);

ALTER TABLE media.media_object ADD CONSTRAINT uq_media_id UNIQUE (media_id);
CREATE INDEX idx_media_robot ON media.media_object (robot_id);
CREATE INDEX idx_media_mission ON media.media_object (mission_id);
CREATE INDEX idx_media_type ON media.media_object (media_type);
CREATE INDEX idx_media_uploaded ON media.media_object (uploaded_at);
CREATE INDEX idx_media_expires ON media.media_object (expires_at);
