-- 歌词识别功能已由前端下线，移除对应的创作者功能权限码。
-- 先清理角色关联，再删除权限行，避免遗留 role_permission 引用。
DELETE rp FROM t_role_permission rp
JOIN t_permission p ON p.id = rp.permission_id
WHERE p.permission_code = 'lyricRecognition';

DELETE FROM t_permission
WHERE permission_code = 'lyricRecognition';
