package com.example.aigc.service;

import com.example.aigc.dto.contract.StudioSkillCreateRequest;
import com.example.aigc.dto.contract.StudioSkillUpdateRequest;
import com.example.aigc.entity.ops.StudioSkill;
import com.example.aigc.exception.BusinessException;
import com.example.aigc.repository.StudioSkillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudioSkillServiceTest {

    @Mock private StudioSkillRepository repository;
    @Mock private ConfigVersionService configVersionService;
    @InjectMocks private StudioSkillService service;

    @Test
    void createUsesDefaultsForOptionalFields() {
        when(repository.save(any(StudioSkill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudioSkillCreateRequest request = new StudioSkillCreateRequest();
        request.setName("模板");

        StudioSkill saved = service.create(request);

        assertEquals("模板", saved.getName());
        assertEquals("", saved.getDescription());
        assertEquals("", saved.getContent());
        assertEquals(Integer.valueOf(1), saved.getEnabled());
        verify(configVersionService).bump();
    }

    @Test
    void updateOnlyChangesProvidedFields() {
        StudioSkill existing = new StudioSkill();
        existing.setId("s1");
        existing.setName("旧名称");
        existing.setDescription("旧描述");
        existing.setContent("旧正文");
        when(repository.findById("s1")).thenReturn(Optional.of(existing));
        when(repository.save(any(StudioSkill.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudioSkillUpdateRequest request = new StudioSkillUpdateRequest();
        request.setDescription("新描述");

        StudioSkill saved = service.update("s1", request);

        assertEquals("旧名称", saved.getName());
        assertEquals("新描述", saved.getDescription());
        assertEquals("旧正文", saved.getContent());
    }

    @Test
    void updateRejectsBlankName() {
        StudioSkill existing = new StudioSkill();
        existing.setId("s1");
        when(repository.findById("s1")).thenReturn(Optional.of(existing));

        StudioSkillUpdateRequest request = new StudioSkillUpdateRequest();
        request.setName("   ");

        assertThrows(BusinessException.class, () -> service.update("s1", request));
        verify(repository, never()).save(any());
    }
}
