-- Function: Create dictionary and i18n tables for F-PLATFORM-003
-- Time: 2026-07-04
-- Author: AxeXie

-- Dictionary type table
CREATE TABLE platform_governance.sys_dict_type (
    id BIGSERIAL PRIMARY KEY,
    type_code VARCHAR(128) NOT NULL,
    type_name VARCHAR(256) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    version INTEGER NOT NULL DEFAULT 1,
    published_version INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(64),
    updated_by VARCHAR(64)
);

ALTER TABLE platform_governance.sys_dict_type ADD CONSTRAINT uq_dict_type_code UNIQUE (type_code);
CREATE INDEX idx_dict_type_status ON platform_governance.sys_dict_type (status);

-- Dictionary item table
CREATE TABLE platform_governance.sys_dict_item (
    id BIGSERIAL PRIMARY KEY,
    type_code VARCHAR(128) NOT NULL,
    item_code VARCHAR(128) NOT NULL,
    item_value VARCHAR(512) NOT NULL,
    label_zh_cn VARCHAR(256),
    label_en_us VARCHAR(256),
    sort_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    extra JSONB,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE platform_governance.sys_dict_item ADD CONSTRAINT uq_dict_item UNIQUE (type_code, item_code);
CREATE INDEX idx_dict_item_type ON platform_governance.sys_dict_item (type_code);

-- I18n resource table
CREATE TABLE platform_governance.sys_i18n_resource (
    id BIGSERIAL PRIMARY KEY,
    resource_key VARCHAR(256) NOT NULL,
    locale VARCHAR(16) NOT NULL,
    resource_value TEXT NOT NULL,
    module VARCHAR(64) NOT NULL DEFAULT 'platform',
    description VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE platform_governance.sys_i18n_resource ADD CONSTRAINT uq_i18n_key_locale UNIQUE (resource_key, locale);
CREATE INDEX idx_i18n_locale ON platform_governance.sys_i18n_resource (locale);
CREATE INDEX idx_i18n_module ON platform_governance.sys_i18n_resource (module);

-- Seed: user status dictionary
INSERT INTO platform_governance.sys_dict_type (type_code, type_name, description, status, version, published_version)
VALUES ('user_status', '用户状态', 'User status enum', 'PUBLISHED', 1, 1);

INSERT INTO platform_governance.sys_dict_item (type_code, item_code, item_value, label_zh_cn, label_en_us, sort_order, status, version)
VALUES
('user_status', 'active', 'ACTIVE', '启用', 'Active', 1, 'ACTIVE', 1),
('user_status', 'disabled', 'DISABLED', '禁用', 'Disabled', 2, 'ACTIVE', 1),
('user_status', 'locked', 'LOCKED', '锁定', 'Locked', 3, 'ACTIVE', 1);

-- Seed: robot status dictionary
INSERT INTO platform_governance.sys_dict_type (type_code, type_name, description, status, version, published_version)
VALUES ('robot_status', '机器人状态', 'Robot status enum', 'PUBLISHED', 1, 1);

INSERT INTO platform_governance.sys_dict_item (type_code, item_code, item_value, label_zh_cn, label_en_us, sort_order, status, version)
VALUES
('robot_status', 'online', 'ONLINE', '在线', 'Online', 1, 'ACTIVE', 1),
('robot_status', 'offline', 'OFFLINE', '离线', 'Offline', 2, 'ACTIVE', 1),
('robot_status', 'busy', 'BUSY', '忙碌', 'Busy', 3, 'ACTIVE', 1),
('robot_status', 'error', 'ERROR', '故障', 'Error', 4, 'ACTIVE', 1),
('robot_status', 'maintenance', 'MAINTENANCE', '维护中', 'Maintenance', 5, 'ACTIVE', 1);

-- Seed: mission status dictionary
INSERT INTO platform_governance.sys_dict_type (type_code, type_name, description, status, version, published_version)
VALUES ('mission_status', '任务状态', 'Mission status enum', 'PUBLISHED', 1, 1);

INSERT INTO platform_governance.sys_dict_item (type_code, item_code, item_value, label_zh_cn, label_en_us, sort_order, status, version)
VALUES
('mission_status', 'pending', 'PENDING', '待执行', 'Pending', 1, 'ACTIVE', 1),
('mission_status', 'planning', 'PLANNING', '规划中', 'Planning', 2, 'ACTIVE', 1),
('mission_status', 'executing', 'EXECUTING', '执行中', 'Executing', 3, 'ACTIVE', 1),
('mission_status', 'paused', 'PAUSED', '已暂停', 'Paused', 4, 'ACTIVE', 1),
('mission_status', 'completed', 'COMPLETED', '已完成', 'Completed', 5, 'ACTIVE', 1),
('mission_status', 'failed', 'FAILED', '已失败', 'Failed', 6, 'ACTIVE', 1),
('mission_status', 'cancelled', 'CANCELLED', '已取消', 'Cancelled', 7, 'ACTIVE', 1);
