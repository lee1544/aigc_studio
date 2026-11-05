package com.example.aigc.web;

import com.example.aigc.entity.Organization;
import com.example.aigc.exception.NodeOwnedServiceException;
import com.example.aigc.exception.UnauthorizedException;
import com.example.aigc.repository.OrganizationRepository;
import com.example.aigc.service.NodeConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /api/internal/node-config：供 Node 服务读取 Java 非计费配置快照。
 * 鉴权用独立 NODE_CONFIG_READ_TOKEN；未配置时 503，凭据缺失/错误 401。
 * 不接受用户/管理员登录 token。Cache-Control: no-store。
 */
@RestController
@RequestMapping("/api/internal")
public class InternalNodeConfigController {

    @Autowired
    private NodeConfigService nodeConfigService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Value("${node.config.read.token:}")
    private String readToken;

    @GetMapping("/node-config")
    public ResponseEntity<Map<String, Object>> nodeConfig(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        requireReadToken(auth);
        Map<String, Object> body = nodeConfigService.snapshot();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Cache-Control", "no-store");
        return ResponseEntity.ok().headers(headers).body(body);
    }

    /**
     * GET /api/internal/organizations：供 Node 组织积分充值/账本查询的只读组织列表。
     * 复用 NODE_CONFIG_READ_TOKEN；未配置 503，凭据错误 401。
     */
    @GetMapping("/organizations")
    public ResponseEntity<Map<String, Object>> organizations(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        requireReadToken(auth);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Organization org : organizationRepository.findAll()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", org.getId());
            item.put("orgCode", org.getOrgCode());
            item.put("orgName", org.getOrgName());
            item.put("status", org.getStatus());
            items.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("total", items.size());
        HttpHeaders headers = new HttpHeaders();
        headers.add("Cache-Control", "no-store");
        return ResponseEntity.ok().headers(headers).body(body);
    }

    private void requireReadToken(String auth) {
        if (readToken == null || readToken.isEmpty()) {
            throw new NodeOwnedServiceException("内部配置读取未启用");
        }
        String presented = "";
        if (auth != null && auth.startsWith("Bearer ")) {
            presented = auth.substring(7);
        }
        if (presented.isEmpty() || !constantTimeEquals(presented, readToken)) {
            throw new UnauthorizedException("内部配置凭据无效");
        }
    }

    /** 定长比较，避免按字节提前返回泄露令牌前缀。 */
    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
