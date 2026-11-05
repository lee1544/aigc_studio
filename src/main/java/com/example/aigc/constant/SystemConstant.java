package com.example.aigc.constant;

/**
 * RBAC 与系统初始化常量。
 *
 * <p>角色层级：
 * <ul>
 *   <li>{@code SUPER_ADMIN}：全局超级管理员（org_id 为 NULL），唯一的登录型管理员，
 *       可登录服务端/客户端两个管理后台；</li>
 *   <li>{@code ORG_ADMIN_{orgId}}：组织创作角色（学校创作账号共享角色），
 *       账号 {@code role=user}，只能登录创作平台；默认无任何权限，由超管分配 25 项 feature；</li>
 *   <li>{@code NORMAL_ADMIN_{orgId}}：已废弃，不再创建/使用；</li>
 *   <li>{@code USER_FEATURES_{userId}}：旧个人 feature 角色，仅历史兼容，
 *       绑定组织创作角色的账号不再使用。</li>
 * </ul>
 *
 * @author Li Jian on 2026/6/21.
 */
public class SystemConstant {

    public static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";

    public static final String ORG_ADMIN_ROLE_CODE_PREFIX = "ORG_ADMIN_";

    /** 组织创作角色后台展示名。 */
    public static final String ORG_ADMIN_ROLE_NAME = "组织创作角色";

    public static final String ORG_ADMIN_ROLE_DESCRIPTION = "学校创作账号共享角色";

    /** 已废弃：不再创建/使用。 */
    @Deprecated
    public static final String NORMAL_ADMIN_ROLE_CODE_PREFIX = "NORMAL_ADMIN_";

    /** 系统初始化时创建的默认组织。 */
    public static final String DEFAULT_ORG_CODE = "DEFAULT_ORG";

    public static final String DEFAULT_ORG_NAME = "默认组织";

    public static final String DEFAULT_ORG_DESCRIPTION = "系统默认组织，承载平台级账号";

    /** 系统初始化时创建的默认超级管理员账号（仅首次创建，不会覆盖已有密码）。 */
    public static final String DEFAULT_ADMIN_USERNAME = "admin";

    public static final String DEFAULT_ADMIN_PASSWORD = "admin123";

    public static final String DEFAULT_ADMIN_REAL_NAME = "超级管理员";

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

    /**
     * 创建组织时不再分配任何默认权限。
     * <p>组织创作角色由超管在管理后台按学校开通 25 项创作 feature。
     */
    public static final String[] ORG_ADMIN_PERMISSIONS = new String[0];

    /** 已废弃：不再创建/使用。 */
    @Deprecated
    public static final String[] NORMAL_ADMIN_PERMISSIONS = new String[0];

    /** 是否组织创作角色编码（ORG_ADMIN_*）。 */
    public static boolean isOrgAdminRoleCode(String roleCode) {
        return roleCode != null && roleCode.startsWith(ORG_ADMIN_ROLE_CODE_PREFIX);
    }

    /** 已废弃：是否普通管理员角色编码（NORMAL_ADMIN_*）。 */
    @Deprecated
    public static boolean isNormalAdminRoleCode(String roleCode) {
        return roleCode != null && roleCode.startsWith(NORMAL_ADMIN_ROLE_CODE_PREFIX);
    }

    /**
     * 是否“登录型管理员”角色。
     * <p>只有 {@code SUPER_ADMIN} 会切换 {@code t_user.role = admin}；
     * {@code ORG_ADMIN_*}（组织创作角色）、{@code NORMAL_ADMIN_*}（已废弃）
     * 与用户自建角色都保持 {@code role=user}，不能登录管理后台。
     */
    public static boolean isAdminRoleCode(String roleCode) {
        return SUPER_ADMIN_ROLE_CODE.equals(roleCode);
    }

    /**
     * 是否为系统保留角色编码：不允许通过角色管理接口新建，
     * 避免构造出与内置角色同名/同前缀的角色。
     */
    public static boolean isReservedRoleCode(String roleCode) {
        if (roleCode == null) {
            return false;
        }
        return SUPER_ADMIN_ROLE_CODE.equals(roleCode)
                || isOrgAdminRoleCode(roleCode)
                || isNormalAdminRoleCode(roleCode)
                || roleCode.startsWith(FeaturePermissions.PERSONAL_ROLE_PREFIX);
    }
}
