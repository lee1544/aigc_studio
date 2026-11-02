-- AIGC Studio 数据库初始化 V1（Flyway 版本化迁移 · 幂等）
-- 说明：由 Flyway 在启动时执行。
--       新库：下面的 CREATE TABLE 一次性建全表 + 索引，再写入种子数据。
--       老库：CREATE TABLE 遇到已存在的表自动跳过，随后“老库结构收敛”段幂等补列/补索引，
--             最后执行种子数据；不删除、不重命名既有对象。
--       后续表结构/数据演进请新增 V2__xxx.sql、V3__xxx.sql，禁止修改本文件。
-- 范围：RBAC 基础表 + 账号功能权限审计 + 模型连接/配置 + Agent/工作流/Skill/系统设置 + 普通项目；
--       不含积分/定价/报价/运行记录等表（由 Node 服务在自家 SQLite 维护，J01 边界）。
-- 密钥：厂商 API Key 不写死在本文件，使用 Flyway 占位符，由环境变量注入：
--       TENCENT_TOKENHUB_KEY / DEEPSEEK_KEY / ALIYUN_BAILIAN_KEY_1 / ALIYUN_BAILIAN_KEY_2
--       未注入时对应模型会被置为 enabled=0，避免“启用但无密钥”的坏状态。

-- ===== 1. RBAC 基础表 =====
CREATE TABLE IF NOT EXISTS t_organization (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    org_code VARCHAR(50) NOT NULL,
    org_name VARCHAR(100) NOT NULL,
    description VARCHAR(255) NULL,
    status INT NULL,
    create_time DATETIME NULL,
    update_time DATETIME NULL,
    UNIQUE KEY uk_org_code (org_code)
);

CREATE TABLE IF NOT EXISTS t_user (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    real_name VARCHAR(50) NULL,
    name VARCHAR(80) NULL,
    email VARCHAR(254) NULL,
    phone VARCHAR(32) NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'user',
    token_version INT NOT NULL DEFAULT 0,
    permission_version INT NOT NULL DEFAULT 0,
    account_source VARCHAR(32) NULL,
    org_id INT NOT NULL DEFAULT 0,
    status INT NULL DEFAULT 1,
    last_login_time DATETIME NULL,
    create_time DATETIME NULL,
    update_time DATETIME NULL,
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_t_user_email (email)
);

CREATE TABLE IF NOT EXISTS t_role (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    role_code VARCHAR(50) NOT NULL,
    role_name VARCHAR(50) NOT NULL,
    description VARCHAR(255) NULL,
    org_id INT NULL,
    status INT NULL,
    create_time DATETIME NULL,
    update_time DATETIME NULL,
    UNIQUE KEY uk_role_code (role_code)
);

CREATE TABLE IF NOT EXISTS t_permission (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    permission_code VARCHAR(100) NOT NULL,
    permission_name VARCHAR(100) NOT NULL,
    status INT NULL DEFAULT 1,
    create_time DATETIME NULL,
    update_time DATETIME NULL,
    UNIQUE KEY uk_permission_code (permission_code)
);

CREATE TABLE IF NOT EXISTS t_role_permission (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    role_id INT NOT NULL,
    permission_id INT NOT NULL,
    UNIQUE KEY uk_role_perm (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS t_user_role (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    role_id INT NOT NULL,
    UNIQUE KEY uk_user_role (user_id, role_id)
);

-- ===== 2. 账号功能权限审计表 =====
CREATE TABLE IF NOT EXISTS permission_audits (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    operator_id INT NOT NULL,
    user_id INT NOT NULL,
    mode VARCHAR(16) NOT NULL,
    before_json TEXT NOT NULL,
    after_json TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_permission_audits_user (user_id, created_at DESC)
);

-- ===== 3. 模型连接与配置 =====
CREATE TABLE IF NOT EXISTS model_connections (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    api_key TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS model_configs (
    feature VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    adapter VARCHAR(64) NOT NULL,
    endpoint VARCHAR(255) NOT NULL,
    api_key TEXT NOT NULL,
    enabled INT NOT NULL DEFAULT 0,
    custom INT NOT NULL DEFAULT 0,
    request_template TEXT NOT NULL,
    connection_id VARCHAR(64) NULL,
    PRIMARY KEY (feature, model)
);

CREATE TABLE IF NOT EXISTS deleted_model_configs (
    feature VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    PRIMARY KEY (feature, model)
);

CREATE TABLE IF NOT EXISTS model_connection_checks (
    connection_id VARCHAR(64) NOT NULL PRIMARY KEY,
    checked_at DATETIME NOT NULL,
    status VARCHAR(16) NOT NULL,
    message TEXT NOT NULL,
    latency_ms INT NOT NULL
);

-- ===== 4. Agent 配置 =====
CREATE TABLE IF NOT EXISTS agent_step_models (
    agent VARCHAR(32) NOT NULL,
    step VARCHAR(32) NOT NULL,
    model VARCHAR(128) NOT NULL,
    PRIMARY KEY (agent, step)
);

CREATE TABLE IF NOT EXISTS agent_system_prompts (
    agent VARCHAR(32) NOT NULL PRIMARY KEY,
    system_prompt TEXT NOT NULL
);

-- ===== 5. 工作流配置（运行/任务归 Node） =====
CREATE TABLE IF NOT EXISTS comfy_workflows (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    api_key TEXT NOT NULL,
    graph TEXT NOT NULL,
    prompt_node VARCHAR(64) NOT NULL,
    prompt_input VARCHAR(64) NOT NULL DEFAULT 'text',
    enabled INT NOT NULL DEFAULT 0,
    created_at DATETIME NULL,
    updated_at DATETIME NULL,
    provider VARCHAR(32) NOT NULL DEFAULT 'comfyui',
    remote_workflow_id VARCHAR(64) NOT NULL DEFAULT '',
    purpose VARCHAR(32) NOT NULL DEFAULT 'general',
    media_bindings TEXT NOT NULL
);

-- ===== 6. Skill 模板 =====
CREATE TABLE IF NOT EXISTS studio_skills (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    content TEXT NOT NULL,
    enabled INT NOT NULL DEFAULT 1,
    created_at DATETIME NULL,
    updated_at DATETIME NULL
);

-- ===== 7. 系统设置（不含 features 平台开关） =====
CREATE TABLE IF NOT EXISTS system_settings (
    id INT NOT NULL PRIMARY KEY,
    data TEXT NOT NULL,
    CHECK (id = 1)
);

-- ===== 8. 配置版本（内部 node-config 快照用） =====
CREATE TABLE IF NOT EXISTS config_versions (
    id INT NOT NULL PRIMARY KEY,
    version INT NOT NULL DEFAULT 1,
    updated_at DATETIME NOT NULL,
    CHECK (id = 1)
);

-- ===== 9. 普通项目 =====
CREATE TABLE IF NOT EXISTS projects (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id INT NULL,
    title VARCHAR(100) NULL,
    type VARCHAR(32) NULL,
    style VARCHAR(32) NULL,
    duration INT NULL,
    status VARCHAR(16) NULL DEFAULT 'draft',
    updated_at DATETIME NULL,
    module VARCHAR(16) NOT NULL DEFAULT 'script'
);

CREATE TABLE IF NOT EXISTS project_contents (
    project_id INT NOT NULL PRIMARY KEY,
    script TEXT NOT NULL,
    scenes TEXT NOT NULL,
    updated_at DATETIME NULL,
    media TEXT NOT NULL
);

-- ===== 9.5 老库结构收敛（幂等；新库全部命中“已存在”，自动跳过） =====
-- 仅对“表已存在但列/索引落后”的老库生效；新库由上面的 CREATE TABLE 一次性建全。
-- 执行前请确认没有重复键数据，否则下面的唯一索引会创建失败并中止迁移：
--   SELECT username, COUNT(*) FROM t_user GROUP BY username HAVING COUNT(*) > 1;
--   SELECT email, COUNT(*) FROM t_user WHERE email IS NOT NULL AND email <> ''
--     GROUP BY email HAVING COUNT(*) > 1;
-- 若存在重复，请先人工合并/清理，再启动应用。

-- t_user：前端契约与权限版本新增列
SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 't_user' AND column_name = 'email');
SET @sql = IF(@c = 0, 'ALTER TABLE t_user ADD COLUMN email VARCHAR(254) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 't_user' AND column_name = 'phone');
SET @sql = IF(@c = 0, 'ALTER TABLE t_user ADD COLUMN phone VARCHAR(32) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 't_user' AND column_name = 'role');
SET @sql = IF(@c = 0, 'ALTER TABLE t_user ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT ''user''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 't_user' AND column_name = 'token_version');
SET @sql = IF(@c = 0, 'ALTER TABLE t_user ADD COLUMN token_version INT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 't_user' AND column_name = 'permission_version');
SET @sql = IF(@c = 0, 'ALTER TABLE t_user ADD COLUMN permission_version INT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 't_user' AND column_name = 'account_source');
SET @sql = IF(@c = 0, 'ALTER TABLE t_user ADD COLUMN account_source VARCHAR(32) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 't_user' AND column_name = 'name');
SET @sql = IF(@c = 0, 'ALTER TABLE t_user ADD COLUMN name VARCHAR(80) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- model_configs：V1 种子 INSERT 会写 connection_id，老表缺列时先补
SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'model_configs' AND column_name = 'connection_id');
SET @sql = IF(@c = 0, 'ALTER TABLE model_configs ADD COLUMN connection_id VARCHAR(64) NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- projects / project_contents：后续版本新增列
SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'projects' AND column_name = 'module');
SET @sql = IF(@c = 0, 'ALTER TABLE projects ADD COLUMN module VARCHAR(16) NOT NULL DEFAULT ''script''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'project_contents' AND column_name = 'media');
SET @sql = IF(@c = 0, 'ALTER TABLE project_contents ADD COLUMN media TEXT NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
UPDATE project_contents SET media = '{}' WHERE media = '' AND @c = 0;

-- comfy_workflows：后续版本新增列
SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'comfy_workflows' AND column_name = 'provider');
SET @sql = IF(@c = 0, 'ALTER TABLE comfy_workflows ADD COLUMN provider VARCHAR(32) NOT NULL DEFAULT ''comfyui''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'comfy_workflows' AND column_name = 'remote_workflow_id');
SET @sql = IF(@c = 0, 'ALTER TABLE comfy_workflows ADD COLUMN remote_workflow_id VARCHAR(64) NOT NULL DEFAULT ''''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'comfy_workflows' AND column_name = 'purpose');
SET @sql = IF(@c = 0, 'ALTER TABLE comfy_workflows ADD COLUMN purpose VARCHAR(32) NOT NULL DEFAULT ''general''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'comfy_workflows' AND column_name = 'media_bindings');
SET @sql = IF(@c = 0, 'ALTER TABLE comfy_workflows ADD COLUMN media_bindings TEXT NOT NULL', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
UPDATE comfy_workflows SET media_bindings = '{}' WHERE media_bindings = '' AND @c = 0;

-- 唯一索引/普通索引：按列组合判断，避免与老库已有的异名索引重复
SET @i = (SELECT COUNT(*) FROM (
    SELECT INDEX_NAME FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 't_user'
      AND non_unique = 0 AND column_name = 'username'
    GROUP BY INDEX_NAME HAVING COUNT(*) = 1
) x);
SET @sql = IF(@i = 0, 'CREATE UNIQUE INDEX uk_username ON t_user(username)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @i = (SELECT COUNT(*) FROM (
    SELECT INDEX_NAME FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 't_user'
      AND non_unique = 0 AND column_name = 'email'
    GROUP BY INDEX_NAME HAVING COUNT(*) = 1
) x);
SET @sql = IF(@i = 0, 'CREATE UNIQUE INDEX uk_t_user_email ON t_user(email)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @i = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'permission_audits' AND index_name = 'idx_permission_audits_user');
SET @sql = IF(@i = 0, 'CREATE INDEX idx_permission_audits_user ON permission_audits(user_id, created_at DESC)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ===== 10. RBAC 初始数据：管理端权限码 =====
INSERT IGNORE INTO t_permission (permission_code, permission_name, status, create_time, update_time) VALUES
('user:view','查看用户',1,NOW(),NOW()),
('user:create','创建用户',1,NOW(),NOW()),
('user:update','更新用户',1,NOW(),NOW()),
('user:delete','删除用户',1,NOW(),NOW()),
('user:assign-role','分配角色',1,NOW(),NOW()),
('user:reset-password','重置密码',1,NOW(),NOW()),
('org:view','查看组织',1,NOW(),NOW()),
('org:create','创建组织',1,NOW(),NOW()),
('org:update','更新组织',1,NOW(),NOW()),
('org:delete','删除组织',1,NOW(),NOW()),
('role:view','查看角色',1,NOW(),NOW()),
('role:create','创建角色',1,NOW(),NOW()),
('role:update','更新角色',1,NOW(),NOW()),
('role:delete','删除角色',1,NOW(),NOW()),
('role:assign-permission','分配权限',1,NOW(),NOW());

-- 默认组织
INSERT IGNORE INTO t_organization (org_code, org_name, description, status, create_time, update_time)
VALUES ('DEFAULT_ORG', '默认组织', '系统默认组织', 1, NOW(), NOW());

-- 全局超级管理员角色（org_id NULL 表示全局）
INSERT INTO t_role (role_code, role_name, description, org_id, status, create_time, update_time)
SELECT 'SUPER_ADMIN', '超级管理员', '系统全局超级管理员', NULL, 1, NOW(), NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM t_role WHERE role_code = 'SUPER_ADMIN' AND org_id IS NULL);

-- 为超级管理员分配所有启用权限
INSERT IGNORE INTO t_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM t_role r JOIN t_permission p ON p.status = 1
WHERE r.role_code = 'SUPER_ADMIN' AND r.org_id IS NULL;

-- 默认超级管理员用户（username=admin，密码=admin123，BCrypt）
INSERT IGNORE INTO t_user (username, password, real_name, name, email, role, org_id, status, account_source, create_time, update_time)
SELECT 'admin', '$2a$12$ovaL7JxZbkPDkqJdzTjvU.TMD3GRw3IXy3pfGfxrYx8vG23NqlgiK', '超级管理员', '超级管理员', 'admin@example.invalid', 'admin', o.id, 1, '系统初始化', NOW(), NOW()
FROM t_organization o WHERE o.org_code = 'DEFAULT_ORG';

-- 为默认管理员分配超级管理员角色
INSERT IGNORE INTO t_user_role (user_id, role_id)
SELECT u.id, r.id FROM t_user u JOIN t_role r ON r.role_code = 'SUPER_ADMIN' AND r.org_id IS NULL
WHERE u.username = 'admin';

-- ===== 11. 种子：26 项 feature 权限码（创作者功能授权） =====
INSERT IGNORE INTO t_permission (permission_code, permission_name, status, create_time, update_time) VALUES
('text-studio','文本工作室',1,NOW(),NOW()),
('script','剧本创作',1,NOW(),NOW()),
('storyboard','分镜',1,NOW(),NOW()),
('grid','九宫格入口',1,NOW(),NOW()),
('textToImage','文生图',1,NOW(),NOW()),
('imageToImage','图生图',1,NOW(),NOW()),
('imageEdit','图片编辑',1,NOW(),NOW()),
('image','图片生成',1,NOW(),NOW()),
('textToVideo','文生视频',1,NOW(),NOW()),
('imageToVideo','图生视频',1,NOW(),NOW()),
('firstLastFrameVideo','首尾帧视频',1,NOW(),NOW()),
('video','视频生成',1,NOW(),NOW()),
('voice','配音',1,NOW(),NOW()),
('music','音乐',1,NOW(),NOW()),
('lyricRecognition','歌词识别',1,NOW(),NOW()),
('digital-human','数字人',1,NOW(),NOW()),
('edit','本地剪辑',1,NOW(),NOW()),
('3d','3D Studio',1,NOW(),NOW()),
('short-drama-agent','短剧 Agent',1,NOW(),NOW()),
('comic-agent','漫剧 Agent',1,NOW(),NOW()),
('canvas','画布',1,NOW(),NOW()),
('director','本地 3D 导演台',1,NOW(),NOW()),
('workflows','通用工作流',1,NOW(),NOW()),
('transform','素材转换',1,NOW(),NOW()),
('recreate','参考视频再创作',1,NOW(),NOW()),
('files','我的生成文件',1,NOW(),NOW());

-- 为超级管理员补分配 26 项 feature 权限
INSERT IGNORE INTO t_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM t_role r JOIN t_permission p
WHERE r.role_code = 'SUPER_ADMIN' AND r.org_id IS NULL
  AND p.permission_code IN (
    'text-studio','script','storyboard','grid','textToImage','imageToImage','imageEdit','image',
    'textToVideo','imageToVideo','firstLastFrameVideo','video','voice','music','lyricRecognition',
    'digital-human','edit','3d','short-drama-agent','comic-agent','canvas','director','workflows',
    'transform','recreate','files');

-- ===== 12. 已有账号补 role 默认值，并按 RBAC 角色映射提升管理员 =====
UPDATE t_user SET role = 'user' WHERE role IS NULL OR role = '';
UPDATE t_user u SET u.role = 'admin'
WHERE EXISTS (
    SELECT 1 FROM t_user_role ur
    JOIN t_role r ON r.id = ur.role_id
    WHERE ur.user_id = u.id
      AND (r.role_code = 'SUPER_ADMIN' OR r.role_code LIKE 'ORG_ADMIN\_%' ESCAPE '\\')
);

-- ===== 13. 模型连接与配置数据导入（含厂商 API Key） =====
INSERT IGNORE INTO model_connections(id,name,provider,base_url,api_key) VALUES
('061f6b4e-fd6e-44c2-86ee-5d32bac96325','腾讯云 TokenHub · 音乐','腾讯云 TokenHub','https://tokenhub.tencentmaas.com','${tencent_tokenhub_key}'),
('b9b5b665-1bf7-40de-b1d0-9bf0c54617de','DeepSeek','DeepSeek','https://api.deepseek.com','${deepseek_key}'),
('ecd7ae30-0cd4-4d04-9994-ec561097cfa0','阿里云百炼 · 北京 · 连接 2','阿里云百炼','https://ws-0y5k5hle5egqstjf.cn-beijing.maas.aliyuncs.com','${aliyun_key_2}'),
('f680d680-9b3f-4f57-8b08-6d8b126ab9be','阿里云百炼 · 北京 · 连接 1','阿里云百炼','https://ws-0y5k5hle5egqstjf.cn-beijing.maas.aliyuncs.com','${aliyun_key_1}');

INSERT IGNORE INTO model_configs(feature,model,provider,adapter,endpoint,api_key,enabled,custom,request_template,connection_id) VALUES
('firstLastFrameVideo','MiniMax/MiniMax-H3','阿里云百炼','dashscope-video','https://ws-0y5k5hle5egqstjf.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/video-generation/video-synthesis','${aliyun_key_2}',IF('${aliyun_key_2}' = '', 0, 1),0,'','ecd7ae30-0cd4-4d04-9994-ec561097cfa0'),
('firstLastFrameVideo','doubao-seedance-2-5-260628','火山方舟','ark-video','https://ark.cn-beijing.volces.com/api/v3/contents/generations/tasks','',0,0,'',NULL),
('firstLastFrameVideo','wan3.0-video','阿里云百炼','dashscope-video','https://ws-0y5k5hle5egqstjf.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/video-generation/video-synthesis','${aliyun_key_2}',IF('${aliyun_key_2}' = '', 0, 1),0,'','ecd7ae30-0cd4-4d04-9994-ec561097cfa0'),
('firstLastFrameVideo','wand-vega-video-1.0-lite','腾讯云 TokenHub','tencent-vega','https://tokenhub.tencentmaas.com/v1/wand/vega-videos/tasks','${tencent_tokenhub_key}',IF('${tencent_tokenhub_key}' = '', 0, 1),0,'','061f6b4e-fd6e-44c2-86ee-5d32bac96325'),
('image','Qwen-Image','AutoDL.Art','autodl-image','https://www.autodl.art/api/v1/images/generations','',0,0,'',NULL),
('image','Z-Image-Turbo','AutoDL.Art','autodl-image','https://www.autodl.art/api/v1/images/generations','',0,0,'',NULL),
('image','doubao-seedream-5-0-pro-260628','火山方舟','ark-image','https://ark.cn-beijing.volces.com/api/v3/images/generations','',0,0,'',NULL),
('image','hy-image-v3','腾讯云 TokenHub','tencent-image','https://tokenhub.tencentmaas.com/v1/wand/hunyuan-image/v3-generation','',0,0,'',NULL),
('image','hy-image-v3.5-preview','腾讯云 TokenHub','tencent-image','https://tokenhub.tencentmaas.com/v1/wand/hunyuan-image/v35-generation','',0,0,'',NULL),
('image','jimeng-image','即梦 AI','pending','','',0,0,'',NULL),
('image','qwen-image-2.1-pro','阿里云百炼','qwen-image','https://dashscope.aliyuncs.com/compatible-mode/v1/images/generations','',0,0,'',NULL),
('image','qwen-image-2.1-turbo','阿里云百炼','qwen-image','https://dashscope.aliyuncs.com/compatible-mode/v1/images/generations','',0,0,'',NULL),
('image','qwen-image-3.0','阿里云百炼','qwen-image','https://dashscope.aliyuncs.com/compatible-mode/v1/images/generations','',0,0,'',NULL),
('image','wan2.5-t2i-preview','阿里云百炼','dashscope-image','https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis','',0,0,'',NULL),
('image','wan2.6-t2i','阿里云百炼','dashscope-image','https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis','',0,0,'',NULL),
('image','wanx2.1-imageedit','阿里云百炼','wanx-image-edit','https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/image-synthesis','',0,0,'',NULL),
('imageEdit','qwen-image-3.0','阿里云百炼','qwen-image-edit','https://ws-0y5k5hle5egqstjf.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation','${aliyun_key_1}',IF('${aliyun_key_1}' = '', 0, 1),0,'','f680d680-9b3f-4f57-8b08-6d8b126ab9be'),
('imageEdit','qwen-image-3.0-pro','阿里云百炼','qwen-image-edit','https://ws-0y5k5hle5egqstjf.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation','${aliyun_key_1}',IF('${aliyun_key_1}' = '', 0, 1),0,'','f680d680-9b3f-4f57-8b08-6d8b126ab9be'),
('imageEdit','wanx2.1-imageedit','阿里云百炼','wanx-image-edit','https://ws-0y5k5hle5egqstjf.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/image2image/image-synthesis','${aliyun_key_1}',IF('${aliyun_key_1}' = '', 0, 1),0,'','f680d680-9b3f-4f57-8b08-6d8b126ab9be'),
('imageToImage','doubao-seedream-5-0-pro-260628','火山方舟','ark-image','https://ark.cn-beijing.volces.com/api/v3/images/generations','',0,0,'',NULL),
('imageToImage','hy-image-v3','腾讯云 TokenHub','tencent-image','https://tokenhub.tencentmaas.com/v1/wand/hunyuan-image/v3-generation','',0,0,'',NULL),
('imageToImage','hy-image-v3.5-preview','腾讯云 TokenHub','tencent-image','https://tokenhub.tencentmaas.com/v1/wand/hunyuan-image/v35-generation','',0,0,'',NULL),
('imageToImage','qwen-image-2.1-pro','阿里云百炼','qwen-image','https://dashscope.aliyuncs.com/compatible-mode/v1/images/generations','',0,0,'',NULL),
('imageToImage','qwen-image-2.1-turbo','阿里云百炼','qwen-image','https://ws-0y5k5hle5egqstjf.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/images/generations','${aliyun_key_1}',IF('${aliyun_key_1}' = '', 0, 1),0,'','f680d680-9b3f-4f57-8b08-6d8b126ab9be'),
('imageToImage','qwen-image-3.0','阿里云百炼','qwen-image','https://ws-0y5k5hle5egqstjf.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/images/generations','${aliyun_key_1}',IF('${aliyun_key_1}' = '', 0, 1),0,'','f680d680-9b3f-4f57-8b08-6d8b126ab9be'),
('imageToImage','wanx2.1-imageedit','阿里云百炼','wanx-image-edit','https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/image-synthesis','',0,0,'',NULL),
('imageToVideo','MiniMax/MiniMax-H3','阿里云百炼','dashscope-video','https://ws-0y5k5hle5egqstjf.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/video-generation/video-synthesis','${aliyun_key_2}',IF('${aliyun_key_2}' = '', 0, 1),0,'','ecd7ae30-0cd4-4d04-9994-ec561097cfa0'),
('imageToVideo','doubao-seedance-2-5-260628','火山方舟','ark-video','https://ark.cn-beijing.volces.com/api/v3/contents/generations/tasks','',0,0,'',NULL),
('imageToVideo','hy-video-v1.5','腾讯云 TokenHub','tencent-video','https://tokenhub.tencentmaas.com/v1/wand/hunyuan-video/generation','',0,0,'',NULL),
('imageToVideo','kling-v3','可灵 AI','kling-video','https://api-beijing.klingai.com/v1/videos/text2video','',0,0,'',NULL),
('imageToVideo','wan2.6-i2v','阿里云百炼','dashscope-image-video','https://dashscope.aliyuncs.com/api/v1/services/aigc/video-generation/video-synthesis','',0,0,'',NULL),
('imageToVideo','wan3.0-video','阿里云百炼','dashscope-video','https://ws-0y5k5hle5egqstjf.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/video-generation/video-synthesis','${aliyun_key_2}',IF('${aliyun_key_2}' = '', 0, 1),0,'','ecd7ae30-0cd4-4d04-9994-ec561097cfa0'),
('imageToVideo','wand-vega-video-1.0-lite','腾讯云 TokenHub','tencent-vega','https://tokenhub.tencentmaas.com/v1/wand/vega-videos/tasks','${tencent_tokenhub_key}',IF('${tencent_tokenhub_key}' = '', 0, 1),0,'','061f6b4e-fd6e-44c2-86ee-5d32bac96325'),
('lyricRecognition','hy-asr-3.0-preview','腾讯云 TokenHub','tencent-asr','https://tokenhub.tencentmaas.com/v1/wand/asrproxy/sync_transcribe','',0,0,'',NULL),
('lyricRecognition','paraformer-v2','阿里云百炼','dashscope-asr','https://dashscope.aliyuncs.com/api/v1/services/audio/asr/transcription','',0,0,'',NULL),
('music','ace-step-1.5','ACE-Step / 自建服务','ace-step-music','','',0,0,'',NULL),
('music','facebook/musicgen-large','Meta / 自建推理服务','hf-audio','','',0,0,'',NULL),
('music','minimax-music-v2.6','腾讯云 TokenHub','tencent-music','https://tokenhub.tencentmaas.com/v1/wand/minimax-music/generation','',0,0,'',NULL),
('music','minimax-music-v3.0','腾讯云 TokenHub','tencent-music','https://tokenhub.tencentmaas.com/v1/wand/minimax-music/generation','${tencent_tokenhub_key}',IF('${tencent_tokenhub_key}' = '', 0, 1),0,'','061f6b4e-fd6e-44c2-86ee-5d32bac96325'),
('music','mureka-music-v9','腾讯云 TokenHub','tencent-mureka','https://tokenhub.tencentmaas.com/v1/wand/mureka-music/generation','${tencent_tokenhub_key}',IF('${tencent_tokenhub_key}' = '', 0, 1),0,'','061f6b4e-fd6e-44c2-86ee-5d32bac96325'),
('music','music-2.5+','MiniMax','minimax-music','https://api.minimaxi.com/v1/music_generation','',0,0,'',NULL),
('music','music-3.0','MiniMax','minimax-music','https://api.minimaxi.com/v1/music_generation','',0,0,'',NULL),
('music','seedmusic','即梦 SeedMusic','pending','','',0,0,'',NULL),
('music','stable-audio-3','Stability AI','stable-audio','https://api.stability.ai/v2beta/audio/stable-audio/text-to-audio','',0,0,'',NULL),
('music','suno-v4.5','Suno','pending','','',0,0,'',NULL),
('music','udio','Udio','pending','','',0,0,'',NULL),
('script','DeepSeek-V3.2','AutoDL.Art','chat','https://www.autodl.art/api/v1/chat/completions','',0,0,'',NULL),
('script','GLM-5','AutoDL.Art','chat','https://www.autodl.art/api/v1/chat/completions','',0,0,'',NULL),
('script','GLM-5.1','AutoDL.Art','chat','https://www.autodl.art/api/v1/chat/completions','',0,0,'',NULL),
('script','Kimi-K2.5','AutoDL.Art','chat','https://www.autodl.art/api/v1/chat/completions','',0,0,'',NULL),
('script','MiniMax-M2.5','AutoDL.Art','chat','https://www.autodl.art/api/v1/chat/completions','',0,0,'',NULL),
('script','Qwen3.5-397B-A17B','AutoDL.Art','chat','https://www.autodl.art/api/v1/chat/completions','',0,0,'',NULL),
('script','deepseek-flash','DeepSeek','chat','https://api.deepseek.com/chat/completions','',0,0,'',NULL),
('script','deepseek-v4-pro','DeepSeek','chat','https://api.deepseek.com/chat/completions','',0,0,'',NULL),
('script','hunyuan-role-latest','腾讯云 TokenHub','chat','https://tokenhub.tencentmaas.com/v1/chat/completions','',0,0,'',NULL),
('script','hy3','腾讯云 TokenHub','chat','https://tokenhub.tencentmaas.com/v1/chat/completions','',0,0,'',NULL),
('script','hy4-preview','腾讯云 TokenHub','chat','https://tokenhub.tencentmaas.com/v1/chat/completions','',0,0,'',NULL),
('script','qwen-plus','阿里云百炼','chat','https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions','',0,0,'',NULL),
('script','qwen3-vl-plus','阿里云百炼','chat','https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions','',0,0,'',NULL),
('storyboard','DeepSeek-V3.2','AutoDL.Art','chat','https://www.autodl.art/api/v1/chat/completions','',0,0,'',NULL),
('storyboard','GLM-5','AutoDL.Art','chat','https://www.autodl.art/api/v1/chat/completions','',0,0,'',NULL),
('storyboard','GLM-5.1','AutoDL.Art','chat','https://www.autodl.art/api/v1/chat/completions','',0,0,'',NULL),
('storyboard','Kimi-K2.5','AutoDL.Art','chat','https://www.autodl.art/api/v1/chat/completions','',0,0,'',NULL),
('storyboard','MiniMax-M2.5','AutoDL.Art','chat','https://www.autodl.art/api/v1/chat/completions','',0,0,'',NULL),
('storyboard','Qwen3.5-397B-A17B','AutoDL.Art','chat','https://www.autodl.art/api/v1/chat/completions','',0,0,'',NULL),
('storyboard','deepseek-flash','DeepSeek','chat','https://api.deepseek.com/chat/completions','',0,0,'',NULL),
('storyboard','deepseek-v4-pro','DeepSeek','chat','https://api.deepseek.com/chat/completions','',0,0,'',NULL),
('storyboard','hunyuan-role-latest','腾讯云 TokenHub','chat','https://tokenhub.tencentmaas.com/v1/chat/completions','',0,0,'',NULL),
('storyboard','hy3','腾讯云 TokenHub','chat','https://tokenhub.tencentmaas.com/v1/chat/completions','',0,0,'',NULL),
('storyboard','hy4-preview','腾讯云 TokenHub','chat','https://tokenhub.tencentmaas.com/v1/chat/completions','',0,0,'',NULL),
('storyboard','qwen-plus','阿里云百炼','chat','https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions','',0,0,'',NULL),
('textGeneration','DeepSeek-V3.2','AutoDL.Art','chat','https://www.autodl.art/api/v1/chat/completions','',0,0,'',NULL),
('textGeneration','GLM-5','AutoDL.Art','chat','https://www.autodl.art/api/v1/chat/completions','',0,0,'',NULL),
('textGeneration','GLM-5.1','AutoDL.Art','chat','https://www.autodl.art/api/v1/chat/completions','',0,0,'',NULL),
('textGeneration','Kimi-K2.5','AutoDL.Art','chat','https://www.autodl.art/api/v1/chat/completions','',0,0,'',NULL),
('textGeneration','MiniMax-M2.5','AutoDL.Art','chat','https://www.autodl.art/api/v1/chat/completions','',0,0,'',NULL),
('textGeneration','Qwen3.5-397B-A17B','AutoDL.Art','chat','https://www.autodl.art/api/v1/chat/completions','',0,0,'',NULL),
('textGeneration','deepseek-flash','DeepSeek','chat','https://api.deepseek.com/chat/completions','${deepseek_key}',IF('${deepseek_key}' = '', 0, 1),0,'','b9b5b665-1bf7-40de-b1d0-9bf0c54617de'),
('textGeneration','deepseek-v4-pro','DeepSeek','chat','https://api.deepseek.com/chat/completions','${deepseek_key}',IF('${deepseek_key}' = '', 0, 1),0,'','b9b5b665-1bf7-40de-b1d0-9bf0c54617de'),
('textGeneration','hunyuan-role-latest','腾讯云 TokenHub','chat','https://tokenhub.tencentmaas.com/v1/chat/completions','',0,0,'',NULL),
('textGeneration','hy3','腾讯云 TokenHub','chat','https://tokenhub.tencentmaas.com/v1/chat/completions','',0,0,'',NULL),
('textGeneration','hy4-preview','腾讯云 TokenHub','chat','https://tokenhub.tencentmaas.com/v1/chat/completions','',0,0,'',NULL),
('textGeneration','qwen-plus','阿里云百炼','chat','https://ws-0y5k5hle5egqstjf.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions','${aliyun_key_1}',IF('${aliyun_key_1}' = '', 0, 1),0,'','f680d680-9b3f-4f57-8b08-6d8b126ab9be'),
('textGeneration','qwen3-vl-plus','阿里云百炼','chat','https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions','',0,0,'',NULL),
('textToImage','Z-Image-Turbo','AutoDL.Art','autodl-image','https://www.autodl.art/api/v1/images/generations','',0,0,'',NULL),
('textToImage','doubao-seedream-5-0-pro-260628','火山方舟','ark-image','https://ark.cn-beijing.volces.com/api/v3/images/generations','',0,0,'',NULL),
('textToImage','hy-image-v3','腾讯云 TokenHub','tencent-image','https://tokenhub.tencentmaas.com/v1/wand/hunyuan-image/v3-generation','',0,0,'',NULL),
('textToImage','hy-image-v3.5-preview','腾讯云 TokenHub','tencent-image','https://tokenhub.tencentmaas.com/v1/wand/hunyuan-image/v35-generation','',0,0,'',NULL),
('textToImage','jimeng-image','即梦 AI','pending','','',0,0,'',NULL),
('textToImage','qwen-image-2.1-pro','阿里云百炼','qwen-image','https://dashscope.aliyuncs.com/compatible-mode/v1/images/generations','',0,0,'',NULL),
('textToImage','qwen-image-2.1-turbo','阿里云百炼','qwen-image','https://ws-0y5k5hle5egqstjf.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/images/generations','${aliyun_key_1}',IF('${aliyun_key_1}' = '', 0, 1),0,'','f680d680-9b3f-4f57-8b08-6d8b126ab9be'),
('textToImage','qwen-image-3.0','阿里云百炼','qwen-image','https://ws-0y5k5hle5egqstjf.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/images/generations','${aliyun_key_1}',IF('${aliyun_key_1}' = '', 0, 1),0,'','f680d680-9b3f-4f57-8b08-6d8b126ab9be'),
('textToImage','wan2.5-t2i-preview','阿里云百炼','dashscope-image','https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis','',0,0,'',NULL),
('textToImage','wan2.6-t2i','阿里云百炼','dashscope-image-sync','https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation','',0,0,'',NULL),
('textToImage','wanx2.1-imageedit','阿里云百炼','wanx-image-edit','https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/image-synthesis','',0,0,'',NULL),
('textToVideo','MiniMax/MiniMax-H3','阿里云百炼','dashscope-video','https://ws-0y5k5hle5egqstjf.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/video-generation/video-synthesis','${aliyun_key_2}',IF('${aliyun_key_2}' = '', 0, 1),0,'','ecd7ae30-0cd4-4d04-9994-ec561097cfa0'),
('textToVideo','doubao-seedance-2-5-260628','火山方舟','ark-video','https://ark.cn-beijing.volces.com/api/v3/contents/generations/tasks','',0,0,'',NULL),
('textToVideo','hy-video-v1.5','腾讯云 TokenHub','tencent-video','https://tokenhub.tencentmaas.com/v1/wand/hunyuan-video/generation','',0,0,'',NULL),
('textToVideo','kling-v3','可灵 AI','kling-video','https://api-beijing.klingai.com/v1/videos/text2video','',0,0,'',NULL),
('textToVideo','wan2.6-t2v','阿里云百炼','dashscope-video','https://dashscope.aliyuncs.com/api/v1/services/aigc/video-generation/video-synthesis','',0,0,'',NULL),
('textToVideo','wan2.7-t2v','阿里云百炼','dashscope-video','https://dashscope.aliyuncs.com/api/v1/services/aigc/video-generation/video-synthesis','',0,0,'',NULL),
('textToVideo','wan3.0-video','阿里云百炼','dashscope-video','https://ws-0y5k5hle5egqstjf.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/video-generation/video-synthesis','${aliyun_key_2}',IF('${aliyun_key_2}' = '', 0, 1),0,'','ecd7ae30-0cd4-4d04-9994-ec561097cfa0'),
('textToVideo','wand-vega-video-1.0-lite','腾讯云 TokenHub','tencent-vega','https://tokenhub.tencentmaas.com/v1/wand/vega-videos/tasks','${tencent_tokenhub_key}',IF('${tencent_tokenhub_key}' = '', 0, 1),0,'','061f6b4e-fd6e-44c2-86ee-5d32bac96325'),
('threeD','P1-20260311','Tripo','tripo-3d','https://api.tripo3d.ai/v2/openapi','',0,0,'',NULL),
('threeD','Tripo/Tripo-H3.1','阿里云百炼','dashscope-3d','https://ws-0y5k5hle5egqstjf.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/video-generation/3d-generation','${aliyun_key_2}',IF('${aliyun_key_2}' = '', 0, 1),0,'','ecd7ae30-0cd4-4d04-9994-ec561097cfa0'),
('threeD','Tripo/Tripo-P1.0','阿里云百炼','dashscope-3d','https://ws-0y5k5hle5egqstjf.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/video-generation/3d-generation','${aliyun_key_2}',0,0,'','ecd7ae30-0cd4-4d04-9994-ec561097cfa0'),
('threeD','hy-3d-3.0','腾讯云 TokenHub','tencent-3d','https://tokenhub.tencentmaas.com/v1/api/3d','',0,0,'',NULL),
('threeD','hy-3d-3.1','腾讯云 TokenHub','tencent-3d','https://tokenhub.tencentmaas.com/v1/api/3d','${tencent_tokenhub_key}',IF('${tencent_tokenhub_key}' = '', 0, 1),0,'','061f6b4e-fd6e-44c2-86ee-5d32bac96325'),
('threeD','rodin-gen-2','Hyper3D','rodin-3d','https://api.hyper3d.com/api/v2','',0,0,'',NULL),
('threeD','tripo-3d-3.1','腾讯云 TokenHub','tencent-3d','https://tokenhub.tencentmaas.com/v1/api/3d','',0,0,'',NULL),
('threeD','tripo-3d-p1','腾讯云 TokenHub','tencent-3d','https://tokenhub.tencentmaas.com/v1/api/3d','',0,0,'',NULL),
('threeD','v3.0-20250812','Tripo','tripo-3d','https://api.tripo3d.ai/v2/openapi','',0,0,'',NULL),
('threeD','v3.1-20260211','Tripo','tripo-3d','https://api.tripo3d.ai/v2/openapi','',0,0,'',NULL),
('video','MiniMax/MiniMax-H3','阿里云百炼','dashscope-video','https://ws-0y5k5hle5egqstjf.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/video-generation/video-synthesis','${aliyun_key_2}',IF('${aliyun_key_2}' = '', 0, 1),0,'','ecd7ae30-0cd4-4d04-9994-ec561097cfa0'),
('video','doubao-seedance-2-5-260628','火山方舟','ark-video','https://ark.cn-beijing.volces.com/api/v3/contents/generations/tasks','',0,0,'',NULL),
('video','fal-ai/sync-lipsync/v2','fal.ai','fal-lipsync','https://queue.fal.run/fal-ai/sync-lipsync/v2','',0,0,'',NULL),
('video','hy-video-v1.5','腾讯云 TokenHub','tencent-video','https://tokenhub.tencentmaas.com/v1/wand/hunyuan-video/generation','',0,0,'',NULL),
('video','kling-v3','可灵 AI','kling-video','https://api-beijing.klingai.com/v1/videos/text2video','',0,0,'',NULL),
('video','videoretalk','阿里云百炼','dashscope-videoretalk','https://ws-0y5k5hle5egqstjf.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/image2video/video-synthesis','${aliyun_key_2}',IF('${aliyun_key_2}' = '', 0, 1),0,'','ecd7ae30-0cd4-4d04-9994-ec561097cfa0'),
('video','wan2.2-s2v','阿里云百炼','dashscope-s2v','https://ws-0y5k5hle5egqstjf.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/image2video/video-synthesis','${aliyun_key_2}',IF('${aliyun_key_2}' = '', 0, 1),0,'','ecd7ae30-0cd4-4d04-9994-ec561097cfa0'),
('video','wan2.6-i2v','阿里云百炼','dashscope-image-video','https://dashscope.aliyuncs.com/api/v1/services/aigc/video-generation/video-synthesis','',0,0,'',NULL),
('video','wan2.6-t2v','阿里云百炼','dashscope-video','https://dashscope.aliyuncs.com/api/v1/services/aigc/video-generation/video-synthesis','',0,0,'',NULL),
('video','wan2.7-t2v','阿里云百炼','dashscope-video','https://dashscope.aliyuncs.com/api/v1/services/aigc/video-generation/video-synthesis','',0,0,'',NULL),
('video','wan3.0-video','阿里云百炼','dashscope-video','https://dashscope.aliyuncs.com/api/v1/services/aigc/video-generation/video-synthesis','',0,0,'',NULL),
('video','wand-vega-video-1.0-lite','腾讯云 TokenHub','tencent-vega','https://tokenhub.tencentmaas.com/v1/wand/vega-videos/tasks','${tencent_tokenhub_key}',IF('${tencent_tokenhub_key}' = '', 0, 1),0,'','061f6b4e-fd6e-44c2-86ee-5d32bac96325'),
('videoReverse','qwen3-vl-plus','阿里云百炼','chat','https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions','',0,0,'',NULL),
('voice','qwen3-tts-flash','阿里云百炼','dashscope-tts','https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation','',0,0,'',NULL),
('voice','qwen3-tts-instruct-flash','阿里云百炼','dashscope-tts','https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation','',0,0,'',NULL);

INSERT IGNORE INTO deleted_model_configs(feature,model) VALUES('textToImage','Qwen-Image');
