package com.example.aigc.service;

import com.example.aigc.constant.FeaturePermissions;
import com.example.aigc.dto.contract.AdminUserListItemDto;
import com.example.aigc.dto.contract.UserMapper;
import com.example.aigc.entity.Organization;
import com.example.aigc.entity.Role;
import com.example.aigc.entity.User;
import com.example.aigc.entity.UserRole;
import com.example.aigc.exception.BusinessException;
import com.example.aigc.repository.OrganizationRepository;
import com.example.aigc.repository.RoleRepository;
import com.example.aigc.repository.UserRepository;
import com.example.aigc.repository.UserRoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * GET /api/admin/users 分页查询：创作者账号 + 关键字搜索 + 状态筛选，
 * 列表项批量补齐 orgName、roleIds/roleNames、features（避免 N+1）。
 */
@Service
public class AdminUserListService {

    private static final Set<String> STATUS_FILTERS = new HashSet<>(Arrays.asList(
            "active", "disabled", "deleted", "all"));

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private FeaturePermissionService featurePermissionService;

    /**
     * @param page    0 基页码
     * @param pageSize 有效范围 1-100，越界按 20
     * @param keyword 模糊匹配 name/realName/email/username/phone，可空
     * @param status  active/disabled/deleted/all，缺省排除 deleted
     */
    @Transactional(readOnly = true)
    public Page<AdminUserListItemDto> page(int page, int pageSize, String keyword, String status) {
        int safePage = Math.max(page, 0);
        int safeSize = (pageSize <= 0 || pageSize > 100) ? 20 : pageSize;
        String rawStatus = status == null ? null : status.trim().toLowerCase();
        final String statusFilter = (rawStatus == null || rawStatus.isEmpty()) ? null : rawStatus;
        if (statusFilter != null && !STATUS_FILTERS.contains(statusFilter)) {
            throw new BusinessException("status 非法，可选值 " + STATUS_FILTERS);
        }
        final String kw = keyword == null || keyword.trim().isEmpty() ? null : keyword.trim();

        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("role"), "user"));
            if ("active".equals(statusFilter)) {
                ps.add(cb.equal(root.get("status"), 1));
            } else if ("disabled".equals(statusFilter)) {
                ps.add(cb.equal(root.get("status"), 0));
            } else if ("deleted".equals(statusFilter)) {
                ps.add(cb.equal(root.get("status"), -1));
            } else if (statusFilter == null) {
                // 缺省与旧行为一致：排除软删除账号
                ps.add(cb.greaterThanOrEqualTo(root.get("status"), 0));
            }
            if (kw != null) {
                String like = "%" + kw.toLowerCase() + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("realName")), like),
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(root.get("phone")), like)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };

        Sort sort = Sort.by(Sort.Direction.DESC, "createTime").and(Sort.by(Sort.Direction.DESC, "id"));
        Page<User> users = userRepository.findAll(spec, PageRequest.of(safePage, safeSize, sort));
        List<AdminUserListItemDto> items = enrich(users.getContent());
        return new PageImpl<>(items, users.getPageable(), users.getTotalElements());
    }

    /** 按页批量补齐 orgName/roleIds/roleNames/features，避免逐条查询。 */
    private List<AdminUserListItemDto> enrich(List<User> users) {
        if (users.isEmpty()) {
            return Collections.emptyList();
        }
        List<Integer> userIds = users.stream().map(User::getId).collect(Collectors.toList());

        Map<Integer, String> orgNames = loadOrgNames(users);

        Map<Integer, List<Integer>> roleIdsByUser = new HashMap<>();
        for (UserRole ur : userRoleRepository.findByUserIdIn(userIds)) {
            roleIdsByUser.computeIfAbsent(ur.getUserId(), k -> new ArrayList<>()).add(ur.getRoleId());
        }
        Set<Integer> allRoleIds = new HashSet<>();
        roleIdsByUser.values().forEach(allRoleIds::addAll);
        Map<Integer, Role> roles = new HashMap<>();
        if (!allRoleIds.isEmpty()) {
            for (Role role : roleRepository.findAllById(allRoleIds)) {
                roles.put(role.getId(), role);
            }
        }

        Map<Integer, Map<String, Boolean>> features = featurePermissionService.getFeaturesBatch(userIds);

        List<AdminUserListItemDto> items = new ArrayList<>(users.size());
        for (User user : users) {
            AdminUserListItemDto dto = UserMapper.toAdminListItem(user);
            dto.setOrgName(orgNames.get(user.getOrgId()));
            List<Integer> roleIds = new ArrayList<>();
            List<String> roleNames = new ArrayList<>();
            for (Integer roleId : roleIdsByUser.getOrDefault(user.getId(), Collections.emptyList())) {
                Role role = roles.get(roleId);
                if (role == null) {
                    continue;
                }
                // 个人 feature 角色不作为业务角色展示
                if (role.getRoleCode() != null && role.getRoleCode().startsWith(FeaturePermissions.PERSONAL_ROLE_PREFIX)) {
                    continue;
                }
                roleIds.add(role.getId());
                roleNames.add(role.getRoleName());
            }
            dto.setRoleIds(roleIds);
            dto.setRoleNames(roleNames);
            Map<String, Boolean> snapshot = features.get(user.getId());
            dto.setFeatures(snapshot == null ? FeaturePermissions.allFalse() : snapshot);
            items.add(dto);
        }
        return items;
    }

    private Map<Integer, String> loadOrgNames(List<User> users) {
        Set<Integer> orgIds = new LinkedHashSet<>();
        for (User user : users) {
            if (user.getOrgId() != null && user.getOrgId() > 0) {
                orgIds.add(user.getOrgId());
            }
        }
        Map<Integer, String> names = new HashMap<>();
        if (orgIds.isEmpty()) {
            return names;
        }
        for (Organization org : organizationRepository.findAllById(orgIds)) {
            names.put(org.getId(), org.getOrgName());
        }
        return names;
    }
}
