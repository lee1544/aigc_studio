package com.example.aigc.service;

import com.example.aigc.constant.FeaturePermissions;
import com.example.aigc.constant.SystemConstant;
import com.example.aigc.dto.contract.AccountPermissionResponseDto;
import com.example.aigc.dto.contract.PermissionResultDto;
import com.example.aigc.dto.contract.PermissionWriteRequest;
import com.example.aigc.entity.Permission;
import com.example.aigc.entity.PermissionAudit;
import com.example.aigc.entity.Role;
import com.example.aigc.entity.RolePermission;
import com.example.aigc.entity.User;
import com.example.aigc.entity.UserRole;
import com.example.aigc.exception.BusinessException;
import com.example.aigc.exception.ConflictException;
import com.example.aigc.exception.NotFoundException;
import com.example.aigc.filter.UserSessionCache;
import com.example.aigc.repository.PermissionAuditRepository;
import com.example.aigc.repository.PermissionRepository;
import com.example.aigc.repository.RolePermissionRepository;
import com.example.aigc.repository.RoleRepository;
import com.example.aigc.repository.UserRepository;
import com.example.aigc.repository.UserRoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 创作者 25 项 feature 权限的读写服务。
 * <p>
 * 模型：每个 role='user' 账号拥有一个个人角色 USER_FEATURES_{userId}，其 t_role_permission
 * 记录该账号已授予的 feature 权限码。对外按契约返回 {features: 25 项布尔}。
 * 版本号取 t_user.permission_version（J03 起与会话 token_version 独立）；replace 必须 expectedVersions 匹配，任一冲突 409 整批不写。
 */
@Service
public class FeaturePermissionService {

    private static final Logger log = LoggerFactory.getLogger(FeaturePermissionService.class);
    private static final int MAX_BATCH = 500;

    @Autowired private UserRepository userRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private RolePermissionRepository rolePermissionRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private PermissionAuditRepository permissionAuditRepository;
    @Autowired private UserSessionCache userSessionCache;
    @Autowired private ObjectMapper objectMapper;

    /** 读取某账号完整 feature 快照（25 项布尔）。超管返回全 false。 */
    public Map<String, Boolean> getFeatures(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("账号不存在"));
        return getEffectiveFeatures(user);
    }

    /**
     * 有效创作权限：绑定组织创作角色（ORG_ADMIN_{orgId}）的账号优先使用共享角色；
     * 否则回退到旧个人角色 USER_FEATURES_{userId}；超管返回全 false。
     */
    public Map<String, Boolean> getEffectiveFeatures(User user) {
        if (user == null) {
            return FeaturePermissions.allFalse();
        }
        if ("admin".equals(user.getRole())) {
            return FeaturePermissions.allFalse();
        }
        Role shared = findBoundOrgCreatorRole(user);
        if (shared != null) {
            return loadFeaturesByRoleId(shared.getId());
        }
        return loadFeatures(user);
    }

    private Role findBoundOrgCreatorRole(User user) {
        if (user.getOrgId() == null || user.getOrgId() <= 0) {
            return null;
        }
        Role shared = roleRepository
                .findByRoleCode(SystemConstant.ORG_ADMIN_ROLE_CODE_PREFIX + user.getOrgId())
                .orElse(null);
        if (shared == null) {
            return null;
        }
        List<Integer> roleIds = userRoleRepository.findRoleIdsByUserId(user.getId());
        return roleIds != null && roleIds.contains(shared.getId()) ? shared : null;
    }

    /**
     * 批量读取多个账号的 feature 快照（列表页用），key=userId，value=25 项布尔。
     * 绑定组织创作角色的账号取共享角色，否则取旧个人角色；都没有则全 false。
     */
    public Map<Integer, Map<String, Boolean>> getFeaturesBatch(List<Integer> userIds) {
        Map<Integer, Map<String, Boolean>> out = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return out;
        }
        for (Integer userId : userIds) {
            out.put(userId, FeaturePermissions.allFalse());
        }

        List<User> users = userRepository.findAllById(userIds);
        if (users.isEmpty()) {
            return out;
        }
        List<String> roleCodes = new ArrayList<>();
        for (User user : users) {
            if (user.getOrgId() != null && user.getOrgId() > 0) {
                roleCodes.add(SystemConstant.ORG_ADMIN_ROLE_CODE_PREFIX + user.getOrgId());
            }
            roleCodes.add(FeaturePermissions.PERSONAL_ROLE(user.getId()));
        }
        List<Role> roles = roleRepository.findByRoleCodeIn(roleCodes);
        if (roles.isEmpty()) {
            return out;
        }
        Map<String, Role> roleByCode = new LinkedHashMap<>();
        for (Role role : roles) {
            roleByCode.put(role.getRoleCode(), role);
        }
        Map<Integer, Set<Integer>> roleIdsByUser = new LinkedHashMap<>();
        for (UserRole link : userRoleRepository.findByUserIdIn(userIds)) {
            roleIdsByUser.computeIfAbsent(link.getUserId(), key -> new HashSet<>()).add(link.getRoleId());
        }

        Map<Integer, Integer> chosenRoleByUser = new LinkedHashMap<>();
        for (User user : users) {
            Set<Integer> bound = roleIdsByUser.getOrDefault(user.getId(), Collections.emptySet());
            Role shared = user.getOrgId() == null || user.getOrgId() <= 0
                    ? null
                    : roleByCode.get(SystemConstant.ORG_ADMIN_ROLE_CODE_PREFIX + user.getOrgId());
            if (shared != null && bound.contains(shared.getId())) {
                chosenRoleByUser.put(user.getId(), shared.getId());
                continue;
            }
            Role personal = roleByCode.get(FeaturePermissions.PERSONAL_ROLE(user.getId()));
            if (personal != null && bound.contains(personal.getId())) {
                chosenRoleByUser.put(user.getId(), personal.getId());
            }
        }
        if (chosenRoleByUser.isEmpty()) {
            return out;
        }

        List<Integer> chosenRoleIds = new ArrayList<>(new HashSet<>(chosenRoleByUser.values()));
        Set<String> featureCodes = new HashSet<>(FeaturePermissions.CODES);
        Map<Integer, List<Integer>> roleToUsers = new LinkedHashMap<>();
        chosenRoleByUser.forEach((userId, roleId) ->
                roleToUsers.computeIfAbsent(roleId, key -> new ArrayList<>()).add(userId));
        for (Object[] row : permissionRepository.findCodesByRoleIds(chosenRoleIds)) {
            Integer roleId = (Integer) row[0];
            String code = (String) row[1];
            if (!featureCodes.contains(code)) {
                continue;
            }
            for (Integer userId : roleToUsers.getOrDefault(roleId, Collections.emptyList())) {
                out.get(userId).put(code, true);
            }
        }
        return out;
    }

    /** GET /api/admin/users/{id}/permissions。 */
    public AccountPermissionResponseDto getAccountPermission(Integer userId, Integer scopeOrgId) {
        User user = requireCreator(userId);
        requireUserInScope(user, scopeOrgId);
        AccountPermissionResponseDto dto = new AccountPermissionResponseDto();
        dto.setId(userId);
        dto.setFeatures(getEffectiveFeatures(user));
        dto.setVersion(String.valueOf(user.getPermissionVersion()));
        return dto;
    }

    /**
     * PUT /api/admin/user-permissions：批量 replace/grant/revoke，事务原子，写审计。
     *
     * @param scopeOrgId 操作者数据范围：null 表示超级管理员，否则目标账号必须属于该组织
     */
    @Transactional
    public PermissionResultDto write(PermissionWriteRequest req, Integer operatorId, Integer scopeOrgId) {
        if (req.getUserIds() == null || req.getUserIds().isEmpty()) {
            throw new BusinessException("userIds 不能为空");
        }
        if (req.getUserIds().size() > MAX_BATCH) {
            throw new BusinessException("单批最多 500 个账号");
        }
        String mode = req.getMode();
        if (!"replace".equals(mode) && !"grant".equals(mode) && !"revoke".equals(mode)) {
            throw new BusinessException("mode 必须为 replace/grant/revoke");
        }
        // 去重 + 正整数校验
        Set<Integer> seen = new HashSet<>();
        for (Integer id : req.getUserIds()) {
            if (id == null || id <= 0) {
                throw new BusinessException("userIds 必须为正整数");
            }
            if (!seen.add(id)) {
                throw new BusinessException("userIds 存在重复: " + id);
            }
        }

        // 校验 features：仅已知 25 项，值为布尔
        Map<String, Boolean> patch = normalizeFeatures(req.getFeatures(), mode);

        // replace 必传 expectedVersions；批量预览提交也应传入
        if ("replace".equals(mode) && (req.getExpectedVersions() == null || req.getExpectedVersions().isEmpty())) {
            throw new BusinessException("replace 模式必须传 expectedVersions");
        }

        List<User> users = new ArrayList<>();
        for (Integer id : seen) {
            User u = userRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("账号不存在: " + id));
            if ("admin".equals(u.getRole())) {
                throw new BusinessException("不能修改管理员账号权限: " + id);
            }
            if (u.getStatus() != null && u.getStatus() == -1) {
                throw new BusinessException("账号已删除: " + id);
            }
            if (findBoundOrgCreatorRole(u) != null) {
                throw new BusinessException("该账号使用组织创作角色，请到角色权限中编辑: " + id);
            }
            requireUserInScope(u, scopeOrgId);
            // replace 校验 expectedVersions（权限版本，非会话版本）
            if (req.getExpectedVersions() != null && req.getExpectedVersions().containsKey(id)) {
                String expected = req.getExpectedVersions().get(id);
                String actual = String.valueOf(u.getPermissionVersion());
                if (!expected.equals(actual)) {
                    throw new ConflictException("账号 " + id + " 权限版本冲突");
                }
            }
            users.add(u);
        }

        Map<Integer, String> newVersions = new LinkedHashMap<>();
        for (User u : users) {
            Map<String, Boolean> before = loadFeatures(u);
            Map<String, Boolean> after = applyMode(before, mode, patch);
            applyFeatures(u, after);
            // 仅权限版本 +1，不增加 token_version，不撤销会话
            u.setPermissionVersion(u.getPermissionVersion() + 1);
            userRepository.save(u);
            userSessionCache.evict(u.getId());
            newVersions.put(u.getId(), String.valueOf(u.getPermissionVersion()));

            saveAudit(operatorId, u.getId(), mode, before, after);
        }

        PermissionResultDto result = new PermissionResultDto();
        result.setOk(true);
        result.setUpdated(users.size());
        result.setVersions(newVersions);
        return result;
    }

    /** 创建创作者账号时调用：建立个人角色并按 features 初始化（features 省略则全 false）。 */
    @Transactional
    public void initForNewUser(User user, Map<String, Boolean> features) {
        Role personal = ensurePersonalRole(user.getId());
        Map<String, Boolean> target = features == null ? FeaturePermissions.allFalse() : normalizeFeatures(features, "replace");
        applyFeatureSet(personal.getId(), target);
    }

    /** 软删除/禁用时不改变权限行，仅清缓存。 */

    // ---------- 内部 ----------

    private User requireCreator(Integer userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("账号不存在"));
        if ("admin".equals(u.getRole())) {
            throw new BusinessException("管理员账号不适用 feature 权限");
        }
        return u;
    }

    /** 组织管理员只能维护本组织创作者的功能权限；跨组织按 404 处理。 */
    private void requireUserInScope(User user, Integer scopeOrgId) {
        if (scopeOrgId != null && !scopeOrgId.equals(user.getOrgId())) {
            throw new NotFoundException("账号不存在: " + user.getId());
        }
    }

    private Map<String, Boolean> loadFeatures(User user) {
        Map<String, Boolean> snap = FeaturePermissions.allFalse();
        Role personal = findPersonalRole(user.getId());
        if (personal == null) {
            return snap;
        }
        return loadFeaturesByRoleId(personal.getId());
    }

    private Map<String, Boolean> loadFeaturesByRoleId(Integer roleId) {
        Map<String, Boolean> snap = FeaturePermissions.allFalse();
        List<Permission> granted = permissionRepository.findAllByRoleId(roleId);
        Set<String> codes = granted.stream().map(Permission::getPermissionCode).collect(Collectors.toSet());
        for (String code : FeaturePermissions.CODES) {
            if (codes.contains(code)) {
                snap.put(code, true);
            }
        }
        return snap;
    }

    private void applyFeatures(User user, Map<String, Boolean> target) {
        Role personal = ensurePersonalRole(user.getId());
        applyFeatureSet(personal.getId(), target);
    }

    private void applyFeatureSet(Integer personalRoleId, Map<String, Boolean> target) {
        rolePermissionRepository.deleteByRoleId(personalRoleId);
        List<RolePermission> toSave = new ArrayList<>();
        for (String code : FeaturePermissions.CODES) {
            Boolean on = target.get(code);
            if (Boolean.TRUE.equals(on)) {
                Permission p = permissionRepository.findByPermissionCode(code)
                        .orElseThrow(() -> new BusinessException("权限未登记: " + code));
                RolePermission rp = new RolePermission();
                rp.setRoleId(personalRoleId);
                rp.setPermissionId(p.getId());
                toSave.add(rp);
            }
        }
        if (!toSave.isEmpty()) {
            rolePermissionRepository.saveAll(toSave);
        }
    }

    private Role ensurePersonalRole(Integer userId) {
        String code = FeaturePermissions.PERSONAL_ROLE(userId);
        return roleRepository.findByRoleCode(code).orElseGet(() -> {
            Role r = new Role();
            r.setRoleCode(code);
            r.setRoleName("个人功能权限 #" + userId);
            r.setDescription("创作者 " + userId + " 的 25 项 feature 授权载体");
            r.setOrgId(null);
            r.setStatus(1);
            r.setCreateTime(LocalDateTime.now());
            r.setUpdateTime(LocalDateTime.now());
            return roleRepository.save(r);
        });
    }

    private Role findPersonalRole(Integer userId) {
        return roleRepository.findByRoleCode(FeaturePermissions.PERSONAL_ROLE(userId)).orElse(null);
    }

    private Map<String, Boolean> applyMode(Map<String, Boolean> before, String mode, Map<String, Boolean> patch) {
        Map<String, Boolean> after = new LinkedHashMap<>(before);
        if ("replace".equals(mode)) {
            // 未传的按 false
            for (String code : FeaturePermissions.CODES) {
                after.put(code, Boolean.TRUE.equals(patch.get(code)));
            }
        } else if ("grant".equals(mode)) {
            for (String code : FeaturePermissions.CODES) {
                if (Boolean.TRUE.equals(patch.get(code))) {
                    after.put(code, true);
                }
            }
        } else { // revoke
            for (String code : FeaturePermissions.CODES) {
                if (Boolean.TRUE.equals(patch.get(code))) {
                    after.put(code, false);
                }
            }
        }
        return after;
    }

    /** 规范化 features：仅已知 25 项，值必须为布尔。grant/revoke 只保留 true 键。 */
    private Map<String, Boolean> normalizeFeatures(Map<String, Boolean> raw, String mode) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        if (raw == null) {
            return out;
        }
        for (Map.Entry<String, Boolean> e : raw.entrySet()) {
            String code = e.getKey();
            if (!FeaturePermissions.isKnown(code)) {
                throw new BusinessException("未知权限名: " + code);
            }
            Boolean val = e.getValue();
            if (val == null) {
                throw new BusinessException("权限值必须为布尔: " + code);
            }
            out.put(code, val);
        }
        return out;
    }

    private void saveAudit(Integer operatorId, Integer userId, String mode,
                           Map<String, Boolean> before, Map<String, Boolean> after) {
        try {
            PermissionAudit audit = new PermissionAudit();
            audit.setId(UUID.randomUUID().toString().replace("-", ""));
            audit.setOperatorId(operatorId);
            audit.setUserId(userId);
            audit.setMode(mode);
            audit.setBeforeJson(objectMapper.writeValueAsString(before));
            audit.setAfterJson(objectMapper.writeValueAsString(after));
            audit.setCreatedAt(LocalDateTime.now());
            permissionAuditRepository.save(audit);
        } catch (Exception e) {
            log.error("保存权限审计失败 user={}", userId, e);
            throw new BusinessException("审计写入失败");
        }
    }
}
