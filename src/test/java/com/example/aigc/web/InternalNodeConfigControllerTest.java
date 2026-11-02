package com.example.aigc.web;

import com.example.aigc.exception.NodeOwnedServiceException;
import com.example.aigc.exception.UnauthorizedException;
import com.example.aigc.service.NodeConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/** J04：内部配置接口鉴权边界（未配置 503、凭据错误 401、no-store）。 */
@ExtendWith(MockitoExtension.class)
class InternalNodeConfigControllerTest {

    @Mock private NodeConfigService nodeConfigService;

    private InternalNodeConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalNodeConfigController();
        ReflectionTestUtils.setField(controller, "nodeConfigService", nodeConfigService);
    }

    @Test
    void closesWhenTokenNotConfigured() {
        ReflectionTestUtils.setField(controller, "readToken", "");
        assertThrows(NodeOwnedServiceException.class,
                () -> controller.nodeConfig("Bearer anything"));
    }

    @Test
    void rejectsMissingOrWrongCredential() {
        ReflectionTestUtils.setField(controller, "readToken", "server-token");
        assertThrows(UnauthorizedException.class, () -> controller.nodeConfig(null));
        assertThrows(UnauthorizedException.class, () -> controller.nodeConfig("Bearer wrong"));
        // 用户/管理员 JWT 不能代替服务凭据
        assertThrows(UnauthorizedException.class, () -> controller.nodeConfig("Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig"));
    }

    @Test
    void returnsSnapshotWithNoStore() {
        ReflectionTestUtils.setField(controller, "readToken", "server-token");
        Map<String, Object> snapshot = Collections.singletonMap("version", "7");
        when(nodeConfigService.snapshot()).thenReturn(snapshot);

        ResponseEntity<Map<String, Object>> resp = controller.nodeConfig("Bearer server-token");

        assertEquals(200, resp.getStatusCodeValue());
        assertEquals("no-store", resp.getHeaders().getFirst("Cache-Control"));
        assertEquals("7", resp.getBody().get("version"));
    }
}
