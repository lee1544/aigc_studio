package com.example.aigc.service;

import com.example.aigc.constant.SystemConstant;
import com.example.aigc.entity.Role;
import com.example.aigc.repository.RoleRepository;
import com.example.aigc.repository.UserRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserRoleService {
    
    @Autowired
    private UserRoleRepository userRoleRepository;
    
    @Autowired
    private RoleRepository roleRepository;
    
    public List<Integer> getRoleIdsByUserId(Integer userId) {
        return userRoleRepository.findRoleIdsByUserId(userId);
    }
    
    public String getRoleCodeById(Integer roleId) {
        return roleRepository.findById(roleId)
                .map(Role::getRoleCode)
                .orElse("");
    }

    public List<String> getRoleCodesByUserId(Integer userId) {
        return getRoleCodesByRoleIds(getRoleIdsByUserId(userId));
    }

    /** 批量把角色 id 转成角色编码；一次查询，避免逐个回表。 */
    public List<String> getRoleCodesByRoleIds(Collection<Integer> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return roleRepository.findAllById(roleIds).stream()
                .map(Role::getRoleCode)
                .filter(code -> code != null && !code.isEmpty())
                .collect(Collectors.toList());
    }

    public boolean isOrgAdmin(Integer userId, Integer orgId) {
        List<Integer> roleIds = getRoleIdsByUserId(userId);
        for (Integer roleId : roleIds) {
            String roleCode = getRoleCodeById(roleId);
            if (SystemConstant.ORG_ADMIN_ROLE_CODE_PREFIX.concat(orgId.toString()).equals(roleCode)) {
                return true;
            }
        }
        return false;
    }

    public boolean isSuperAdmin(Integer userId) {
        return isSuperAdminByRoleIds(getRoleIdsByUserId(userId));
    }

    /** 给定角色 id 集合中是否包含全局超级管理员角色。 */
    public boolean isSuperAdminByRoleIds(Collection<Integer> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return false;
        }
        return roleRepository.findAllById(roleIds).stream()
                .anyMatch(role -> SystemConstant.SUPER_ADMIN_ROLE_CODE.equals(role.getRoleCode()));
    }

    public UserRoleInfo getUserRoleInfo(Integer userId, Integer orgId) {
        List<String> roleCodes = getRoleCodesByUserId(userId);
        boolean isSuperAdmin = roleCodes.contains(SystemConstant.SUPER_ADMIN_ROLE_CODE);
        boolean isOrgAdmin = roleCodes.stream()
                .anyMatch(code -> SystemConstant.ORG_ADMIN_ROLE_CODE_PREFIX.concat(orgId.toString()).equals(code));
        return new UserRoleInfo(isSuperAdmin, isOrgAdmin);
    }

    public static class UserRoleInfo {
        private final boolean superAdmin;
        private final boolean orgAdmin;

        public UserRoleInfo(boolean superAdmin, boolean orgAdmin) {
            this.superAdmin = superAdmin;
            this.orgAdmin = orgAdmin;
        }

        public boolean isSuperAdmin() { return superAdmin; }
        public boolean isOrgAdmin() { return orgAdmin; }
    }
}
