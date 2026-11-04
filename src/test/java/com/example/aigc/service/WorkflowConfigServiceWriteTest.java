package com.example.aigc.service;

import com.example.aigc.dto.contract.WorkflowWriteRequest;
import com.example.aigc.entity.config.ComfyWorkflow;
import com.example.aigc.exception.BusinessException;
import com.example.aigc.repository.ComfyWorkflowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowConfigServiceWriteTest {

    @Mock private ComfyWorkflowRepository repository;
    @Mock private ConfigVersionService configVersionService;

    private WorkflowConfigService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowConfigService();
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "configVersionService", configVersionService);
    }

    @Test
    void createKeepsGraphStringAndSerializesMediaBindingsObject() {
        when(repository.save(any(ComfyWorkflow.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowWriteRequest request = new WorkflowWriteRequest();
        request.setName("出图");
        request.setBaseUrl("http://comfy:8188");
        request.setGraph(JsonNodeFactory.instance.textNode("{\"1\":{}}"));
        request.setMediaBindings(JsonNodeFactory.instance.objectNode().put("image", "node-1"));
        request.setEnabled(true);

        service.create(request);

        verify(repository).save(argThat(w ->
                "{\"1\":{}}".equals(w.getGraph())
                        && "{\"image\":\"node-1\"}".equals(w.getMediaBindings())
                        && Integer.valueOf(1).equals(w.getEnabled())));
    }

    @Test
    void createRequiresNameAndBaseUrl() {
        WorkflowWriteRequest request = new WorkflowWriteRequest();
        request.setName("出图");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request));

        assertEquals("baseUrl 不能为空", ex.getMessage());
        verify(repository, never()).save(any());
    }

    @Test
    void updateKeepsOldApiKeyWhenEmpty() {
        ComfyWorkflow existing = new ComfyWorkflow();
        existing.setId("wf-1");
        existing.setApiKey("secret");
        when(repository.findById("wf-1")).thenReturn(Optional.of(existing));
        when(repository.save(any(ComfyWorkflow.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowWriteRequest request = new WorkflowWriteRequest();
        request.setApiKey("");
        request.setEnabled(false);

        service.update("wf-1", request);

        assertEquals("secret", existing.getApiKey());
        assertEquals(Integer.valueOf(0), existing.getEnabled());
    }
}
