-- projects.module 需要容纳 feature 编码（如 short-drama-agent，17 个字符）。
-- 原 VARCHAR(16) 会触发 MySQL 1406 Data too long，Hibernate 包装成 DataException 后返回 500。
-- 新库先由 V1 建表，再由本脚本加宽；老库同样适用。V1 已应用，不能修改其校验和。
ALTER TABLE projects MODIFY COLUMN module VARCHAR(64) NOT NULL DEFAULT 'script';
