package com.example.aigc.service;

import com.example.aigc.dto.contract.ProjectContentRequest;
import com.example.aigc.dto.contract.ProjectCreateRequest;
import com.example.aigc.entity.project.Project;
import com.example.aigc.entity.project.ProjectContent;
import com.example.aigc.exception.BusinessException;
import com.example.aigc.exception.NotFoundException;
import com.example.aigc.repository.ProjectContentRepository;
import com.example.aigc.repository.ProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 普通项目持久化（/api/projects）。归属当前登录用户，不信任 body.userId。
 * content 原样保存 script/scenes/media；module 更新落库。
 */
@Service
public class ProjectService {

    /** 与 projects 表列长度保持一致，避免超长值落到数据库才报 DataException。 */
    private static final int MAX_TITLE = 100;
    private static final int MAX_TYPE = 32;
    private static final int MAX_STYLE = 32;
    private static final int MAX_MODULE = 64;

    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectContentRepository contentRepository;
    @Autowired private ObjectMapper objectMapper;

    public List<Project> list(Integer userId) {
        return projectRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    /** 分页版本；page/size 由 Controller 统一做边界收敛。 */
    public Page<Project> list(Integer userId, int page, int size) {
        return projectRepository.findByUserIdOrderByUpdatedAtDesc(userId, PageRequest.of(page, size));
    }

    @Transactional
    public Project create(Integer userId, ProjectCreateRequest request) {
        Project p = new Project();
        p.setUserId(userId);
        p.setTitle(checked("title", request.getTitle(), MAX_TITLE));
        p.setType(checked("type", request.getType(), MAX_TYPE));
        p.setStyle(checked("style", request.getStyle(), MAX_STYLE));
        p.setDuration(request.getDuration());
        p.setStatus("draft");
        String module = request.getModule();
        p.setModule(checked("module", module == null || module.isEmpty() ? "script" : module, MAX_MODULE));
        p.setUpdatedAt(LocalDateTime.now());
        p = projectRepository.save(p);
        // 初始化空正文
        ProjectContent c = new ProjectContent();
        c.setProjectId(p.getId());
        c.setScript("");
        c.setScenes("[]");
        c.setMedia("{}");
        c.setUpdatedAt(LocalDateTime.now());
        contentRepository.save(c);
        return p;
    }

    public Project requireOwned(Integer userId, Integer id) {
        Project p = projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("项目不存在"));
        if (!userId.equals(p.getUserId())) {
            throw new NotFoundException("项目不存在");
        }
        return p;
    }

    public Map<String, Object> content(Integer userId, Integer id) {
        requireOwned(userId, id);
        ProjectContent c = contentRepository.findById(id).orElseGet(() -> {
            ProjectContent fresh = new ProjectContent();
            fresh.setProjectId(id);
            fresh.setScript("");
            fresh.setScenes("[]");
            fresh.setMedia("{}");
            return fresh;
        });
        return contentView(c);
    }

    @Transactional
    public Map<String, Object> saveContent(Integer userId, Integer id, ProjectContentRequest request) {
        Project p = requireOwned(userId, id);
        ProjectContent c = contentRepository.findById(id).orElseGet(() -> {
            ProjectContent fresh = new ProjectContent();
            fresh.setProjectId(id);
            return fresh;
        });
        c.setProjectId(id);
        c.setScript(request.getScript() == null ? "" : request.getScript());
        c.setScenes(jsonValue(request.getScenes(), "[]"));
        c.setMedia(jsonValue(request.getMedia(), "{}"));
        c.setUpdatedAt(LocalDateTime.now());
        contentRepository.save(c);
        p.setUpdatedAt(LocalDateTime.now());
        projectRepository.save(p);
        return contentView(c);
    }

    @Transactional
    public Project patchModule(Integer userId, Integer id, String module) {
        if (module == null || module.isEmpty()) {
            throw new BusinessException("module 不能为空");
        }
        module = checked("module", module, MAX_MODULE);
        Project p = requireOwned(userId, id);
        p.setModule(module);
        p.setUpdatedAt(LocalDateTime.now());
        return projectRepository.save(p);
    }

    @Transactional
    public void delete(Integer userId, Integer id) {
        Project p = requireOwned(userId, id);
        contentRepository.findById(id).ifPresent(contentRepository::delete);
        projectRepository.delete(p);
    }

    private Map<String, Object> contentView(ProjectContent c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("projectId", c.getProjectId());
        m.put("script", c.getScript());
        m.put("scenes", parse(c.getScenes()));
        m.put("media", parse(c.getMedia()));
        m.put("updatedAt", c.getUpdatedAt());
        return m;
    }

    private Object parse(String json) {
        try { return objectMapper.readTree(json == null ? "{}" : json); }
        catch (Exception e) { return json; }
    }

    /** 字符串按原样保存；对象/数组序列化为 JSON 文本；缺省值用于 null。 */
    private String jsonValue(JsonNode node, String defaultValue) {
        if (node == null || node.isNull()) return defaultValue;
        return node.isTextual() ? node.asText() : node.toString();
    }

    /** 超长直接 400，避免把 Data too long 泄漏成 500。 */
    private String checked(String field, String value, int max) {
        if (value != null && value.length() > max) {
            throw new BusinessException(field + " 长度不能超过 " + max + " 个字符");
        }
        return value;
    }
}
