package com.example.aigc.service;

import com.example.aigc.entity.project.Project;
import com.example.aigc.entity.project.ProjectContent;
import com.example.aigc.exception.BusinessException;
import com.example.aigc.exception.NotFoundException;
import com.example.aigc.repository.ProjectContentRepository;
import com.example.aigc.repository.ProjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectContentRepository contentRepository;
    @Autowired private ObjectMapper objectMapper;

    public List<Project> list(Integer userId) {
        return projectRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    @Transactional
    public Project create(Integer userId, Map<String, Object> body) {
        Project p = new Project();
        p.setUserId(userId);
        p.setTitle(text(body, "title"));
        p.setType(text(body, "type"));
        p.setStyle(text(body, "style"));
        Object dur = body.get("duration");
        if (dur != null) p.setDuration(toInt(dur));
        p.setStatus("draft");
        p.setModule(textOrDefault(body, "module", "script"));
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
    public Map<String, Object> saveContent(Integer userId, Integer id, Map<String, Object> body) {
        Project p = requireOwned(userId, id);
        ProjectContent c = contentRepository.findById(id).orElseGet(() -> {
            ProjectContent fresh = new ProjectContent();
            fresh.setProjectId(id);
            return fresh;
        });
        c.setProjectId(id);
        c.setScript(textOrDefault(body, "script", ""));
        Object scenes = body.get("scenes");
        c.setScenes(scenes == null ? "[]" : (scenes instanceof String ? (String) scenes : toJson(scenes)));
        Object media = body.get("media");
        c.setMedia(media == null ? "{}" : (media instanceof String ? (String) media : toJson(media)));
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

    private String toJson(Object o) {
        try { return objectMapper.writeValueAsString(o); } catch (Exception e) { return "{}"; }
    }

    private String text(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private String textOrDefault(Map<String, Object> body, String key, String def) {
        String v = text(body, key);
        return v == null || v.isEmpty() ? def : v;
    }

    private Integer toInt(Object v) {
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return null; }
    }
}
