package com.example.aigc.web;

import com.example.aigc.annotation.RequirePermission;
import com.example.aigc.dto.contract.AccountPermissionResponseDto;
import com.example.aigc.dto.contract.PermissionResultDto;
import com.example.aigc.dto.contract.PermissionWriteRequest;
import com.example.aigc.entity.PermissionAudit;
import com.example.aigc.entity.User;
import com.example.aigc.exception.NotFoundException;
import com.example.aigc.repository.PermissionAuditRepository;
import com.example.aigc.repository.UserRepository;
import com.example.aigc.service.FeaturePermissionService;
import com.example.aigc.utils.UserContextUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 账号功能权限管理：/api/admin/users/{id}/permissions、/api/admin/user-permissions、
 * /api/admin/permission-audits。
 * <p>组织管理员只能操作本组织账号；超级管理员可操作全部账号。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminPermissionController {

    @Autowired
    private FeaturePermissionService featurePermissionService;

    @Autowired
    private PermissionAuditRepository permissionAuditRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @RequirePermission("user:view")
    @GetMapping("/users/{id}/permissions")
    public AccountPermissionResponseDto getPermissions(@PathVariable Integer id) {
        return featurePermissionService.getAccountPermission(id, UserContextUtil.getVisibleOrgId());
    }

    @RequirePermission("user:update")
    @PutMapping("/user-permissions")
    public PermissionResultDto writePermissions(@Valid @RequestBody PermissionWriteRequest req) {
        return featurePermissionService.write(
                req, UserContextUtil.getCurrentUserId(), UserContextUtil.getVisibleOrgId());
    }

    @RequirePermission("role:view")
    @GetMapping("/permission-audits")
    public List<Map<String, Object>> permissionAudits(
            @RequestParam(name = "userId", required = false) Integer userId) {
        Integer scopeOrgId = UserContextUtil.getVisibleOrgId();
        if (scopeOrgId != null && userId != null) {
            User target = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("账号不存在"));
            if (!Objects.equals(scopeOrgId, target.getOrgId())) {
                throw new NotFoundException("账号不存在");
            }
        }
        List<PermissionAudit> rows = (userId == null)
                ? permissionAuditRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 100))
                : permissionAuditRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 100));
        rows = filterByScope(rows, scopeOrgId);
        return rows.stream().map(this::toView).collect(Collectors.toList());
    }

    /** 组织管理员只能看到本组织账号的审计记录。 */
    private List<PermissionAudit> filterByScope(List<PermissionAudit> rows, Integer scopeOrgId) {
        if (scopeOrgId == null || rows.isEmpty()) {
            return rows;
        }
        List<Integer> userIds = rows.stream()
                .map(PermissionAudit::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<Integer, Integer> orgByUser = new HashMap<>();
        for (User user : userRepository.findAllById(userIds)) {
            orgByUser.put(user.getId(), user.getOrgId());
        }
        List<PermissionAudit> filtered = new ArrayList<>();
        for (PermissionAudit audit : rows) {
            if (scopeOrgId.equals(orgByUser.get(audit.getUserId()))) {
                filtered.add(audit);
            }
        }
        return filtered;
    }

    private Map<String, Object> toView(PermissionAudit a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("operator_id", a.getOperatorId());
        m.put("user_id", a.getUserId());
        m.put("mode", a.getMode());
        m.put("created_at", a.getCreatedAt());
        try {
            m.put("before", objectMapper.readTree(a.getBeforeJson()));
            m.put("after", objectMapper.readTree(a.getAfterJson()));
        } catch (Exception ignored) {
            m.put("before", a.getBeforeJson());
            m.put("after", a.getAfterJson());
        }
        return m;
    }
}
