package com.example.aigc.constant;

/**
 * @author Li Jian on 2026/6/21.
 */
public class SystemConstant {

    public static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";

    public static final String ORG_ADMIN_ROLE_CODE_PREFIX = "ORG_ADMIN_";

    public static final String NORMAL_ADMIN_ROLE_CODE_PREFIX = "NORMAL_ADMIN_";

    public static final String DEFAULT_SYSTEM_PASSWORD = "12345678";

    /**
     * 管理后台权限码前缀。管理后台的权限管理与角色授权只展示这些后端 RBAC 权限，
     * 创作者 feature 权限（无冒号编码）不在管理后台角色分配中展示。
     */
    public static final String[] BACKEND_PERMISSION_PREFIXES = {
            "user:", "org:", "role:", "permission:", "license:"
    };

    public static boolean isBackendPermissionCode(String code) {
        if (code == null) {
            return false;
        }
        for (String prefix : BACKEND_PERMISSION_PREFIXES) {
            if (code.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static final String[] ORG_ADMIN_PERMISSIONS = new String[]{
            "user:view", "user:create", "user:update", "user:delete", "user:assign-role", "user:reset-password",
            "org:view", "org:update",
            "role:view", "role:create", "role:update", "role:delete", "role:assign-permission"
    };

    public static final String[] NORMAL_ADMIN_PERMISSIONS = new String[]{
            "user:view", "user:update"
    };
}
