package com.example.aigc.service;

import com.example.aigc.entity.config.ComfyWorkflow;
import com.example.aigc.exception.BusinessException;
import com.example.aigc.exception.NotFoundException;
import com.example.aigc.repository.ComfyWorkflowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 工作流 check 必须满足 WorkflowCheck 契约的 configurationOnly 分支：ok + configurationOnly + message 三个必备字段，
 * 配置不完整时报明确错误，不能固定返回成功。
 */
@ExtendWith(MockitoExtension.class)
class WorkflowConfigServiceCheckTest {

    @Mock private ComfyWorkflowRepository repository;

    private WorkflowConfigService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowConfigService();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    private ComfyWorkflow complete() {
        ComfyWorkflow w = new ComfyWorkflow();
        w.setId("wf-1");
        w.setName("出图流程");
        w.setBaseUrl("http://comfy.test:8188");
        w.setGraph("{\"1\":{}}");
        w.setPromptNode("6");
        w.setEnabled(1);
        return w;
    }

    @Test
    void checkReturnsContractOkFields() {
        when(repository.findById("wf-1")).thenReturn(Optional.of(complete()));

        Map<String, Object> m = service.check("wf-1");

        assertEquals(Boolean.TRUE, m.get("ok"));
        assertEquals(Boolean.TRUE, m.get("configurationOnly"));
        assertEquals("配置校验通过，未执行真实生成", m.get("message"));
    }

    @Test
    void incompleteConfigurationIsExplicitError() {
        ComfyWorkflow w = complete();
        w.setBaseUrl("");
        when(repository.findById("wf-1")).thenReturn(Optional.of(w));

        assertThrows(BusinessException.class, () -> service.check("wf-1"));
    }

    @Test
    void unknownWorkflowIsNotFound() {
        when(repository.findById("wf-x")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.check("wf-x"));
    }
}
