package com.example.aigc.web;

import com.example.aigc.dto.contract.AccountPermissionResponseDto;
import com.example.aigc.dto.contract.PermissionResultDto;
import com.example.aigc.dto.contract.PermissionWriteRequest;
import com.example.aigc.entity.PermissionAudit;
import com.example.aigc.repository.PermissionAuditRepository;
import com.example.aigc.service.FeaturePermissionService;
import com.example.aigc.utils.UserContextUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 账号功能权限管理：/api/admin/users/{id}/permissions、/api/admin/user-permissions、
 * /api/admin/permission-audits。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminPermissionController {

    @Autowired
    private FeaturePermissionService featurePermissionService;

    @Autowired
    private PermissionAuditRepository permissionAuditRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @GetMapping("/users/{id}/permissions")
    public AccountPermissionResponseDto getPermissions(@PathVariable Integer id) {
        return featurePermissionService.getAccountPermission(id);
    }

    @PutMapping("/user-permissions")
    public PermissionResultDto writePermissions(@Valid @RequestBody PermissionWriteRequest req) {
        return featurePermissionService.write(req, UserContextUtil.getCurrentUserId());
    }

    @GetMapping("/permission-audits")
    public List<Map<String, Object>> permissionAudits(@RequestParam(name = "userId", required = false) Integer userId) {
        List<PermissionAudit> rows = (userId == null)
                ? permissionAuditRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 100))
                : permissionAuditRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 100));
        return rows.stream().map(this::toView).collect(Collectors.toList());
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
