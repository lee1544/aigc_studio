package com.example.aigc.web;

import com.example.aigc.exception.NodeOwnedServiceException;
import com.example.aigc.exception.UnauthorizedException;
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

    @Value("${node.config.read.token:}")
    private String readToken;

    @GetMapping("/node-config")
    public ResponseEntity<Map<String, Object>> nodeConfig(
            @RequestHeader(value = "Authorization", required = false) String auth) {
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
        Map<String, Object> body = nodeConfigService.snapshot();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Cache-Control", "no-store");
        return ResponseEntity.ok().headers(headers).body(body);
    }

    /** 定长比较，避免按字节提前返回泄露令牌前缀。 */
    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
