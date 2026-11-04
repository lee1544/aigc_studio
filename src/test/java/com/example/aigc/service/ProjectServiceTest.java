package com.example.aigc.service;

import com.example.aigc.dto.contract.ProjectContentRequest;
import com.example.aigc.dto.contract.ProjectCreateRequest;
import com.example.aigc.entity.project.Project;
import com.example.aigc.entity.project.ProjectContent;
import com.example.aigc.exception.BusinessException;
import com.example.aigc.repository.ProjectContentRepository;
import com.example.aigc.repository.ProjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectContentRepository contentRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void createAllowsFeatureCodeAsModule() {
        ProjectCreateRequest request = new ProjectCreateRequest();
        request.setTitle("短剧");
        request.setModule("short-drama-agent");
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project p = invocation.getArgument(0);
            p.setId(1);
            return p;
        });

        Project created = projectService.create(7, request);

        assertEquals("short-drama-agent", created.getModule());
        verify(contentRepository).save(any(ProjectContent.class));
    }

    @Test
    void createDefaultsModuleWhenOmitted() {
        ProjectCreateRequest request = new ProjectCreateRequest();
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project p = invocation.getArgument(0);
            p.setId(1);
            return p;
        });

        assertEquals("script", projectService.create(7, request).getModule());
    }

    @Test
    void createRejectsTooLongTitle() {
        ProjectCreateRequest request = new ProjectCreateRequest();
        request.setTitle(repeat('a', 101));

        BusinessException ex = assertThrows(BusinessException.class, () -> projectService.create(7, request));

        assertEquals("title 长度不能超过 100 个字符", ex.getMessage());
        verify(projectRepository, never()).save(any());
    }

    @Test
    void createRejectsTooLongType() {
        ProjectCreateRequest request = new ProjectCreateRequest();
        request.setType(repeat('a', 33));

        assertThrows(BusinessException.class, () -> projectService.create(7, request));
        verify(projectRepository, never()).save(any());
    }

    @Test
    void createRejectsTooLongModule() {
        ProjectCreateRequest request = new ProjectCreateRequest();
        request.setModule(repeat('a', 65));

        BusinessException ex = assertThrows(BusinessException.class, () -> projectService.create(7, request));

        assertEquals("module 长度不能超过 64 个字符", ex.getMessage());
        verify(projectRepository, never()).save(any());
    }

    @Test
    void patchModuleRejectsTooLongModule() {
        assertThrows(BusinessException.class, () -> projectService.patchModule(7, 1, repeat('a', 65)));
        verify(projectRepository, never()).save(any());
    }

    @Test
    void patchModuleAcceptsFeatureCode() {
        Project project = new Project();
        project.setId(1);
        project.setUserId(7);
        when(projectRepository.findById(1)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Project saved = projectService.patchModule(7, 1, "short-drama-agent");

        assertEquals("short-drama-agent", saved.getModule());
    }

    @Test
    void saveContentSerializesObjectsAndKeepsRawStrings() {
        Project project = new Project();
        project.setId(1);
        project.setUserId(7);
        when(projectRepository.findById(1)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(contentRepository.findById(1)).thenReturn(Optional.empty());
        when(contentRepository.save(any(ProjectContent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectContentRequest request = new ProjectContentRequest();
        request.setScript("正文");
        request.setScenes(JsonNodeFactory.instance.arrayNode());
        request.setMedia(JsonNodeFactory.instance.objectNode());

        Map<String, Object> view = projectService.saveContent(7, 1, request);

        assertEquals("正文", view.get("script"));
        verify(contentRepository).save(argThat(content ->
                "[]".equals(content.getScenes()) && "{}".equals(content.getMedia())));
    }

    @Test
    void saveContentUsesDefaultsWhenFieldsMissing() {
        Project project = new Project();
        project.setId(1);
        project.setUserId(7);
        when(projectRepository.findById(1)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(contentRepository.findById(1)).thenReturn(Optional.empty());
        when(contentRepository.save(any(ProjectContent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        projectService.saveContent(7, 1, new ProjectContentRequest());

        verify(contentRepository).save(argThat(content ->
                "".equals(content.getScript())
                        && "[]".equals(content.getScenes())
                        && "{}".equals(content.getMedia())));
    }

    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
