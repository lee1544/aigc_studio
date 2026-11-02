package com.example.aigc.service;

import com.example.aigc.entity.ops.StudioSkill;
import com.example.aigc.exception.BusinessException;
import com.example.aigc.exception.NotFoundException;
import com.example.aigc.repository.StudioSkillRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 平台 Skill 模板 CRUD。 */
@Service
public class StudioSkillService {

    @Autowired private StudioSkillRepository repository;
    @Autowired private ConfigVersionService configVersionService;

    public List<StudioSkill> list() {
        return repository.findAll();
    }

    public List<StudioSkill> listEnabled() {
        return repository.findByEnabled(1);
    }

    @Transactional
    public StudioSkill create(Map<String, Object> body) {
        StudioSkill s = new StudioSkill();
        s.setId(UUID.randomUUID().toString().replace("-", ""));
        s.setName(require(body, "name"));
        s.setDescription(body.get("description") == null ? "" : String.valueOf(body.get("description")));
        s.setContent(body.get("content") == null ? "" : String.valueOf(body.get("content")));
        s.setEnabled(body.get("enabled") == null ? 1
                : (Boolean.TRUE.equals(body.get("enabled")) ? 1 : 0));
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        StudioSkill saved = repository.save(s);
        configVersionService.bump();
        return saved;
    }

    @Transactional
    public StudioSkill update(String id, Map<String, Object> body) {
        StudioSkill s = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Skill 不存在"));
        if (body.get("name") != null) s.setName(String.valueOf(body.get("name")));
        if (body.get("description") != null) s.setDescription(String.valueOf(body.get("description")));
        if (body.get("content") != null) s.setContent(String.valueOf(body.get("content")));
        if (body.get("enabled") != null) {
            s.setEnabled(Boolean.TRUE.equals(body.get("enabled")) ? 1 : 0);
        }
        s.setUpdatedAt(LocalDateTime.now());
        StudioSkill saved = repository.save(s);
        configVersionService.bump();
        return saved;
    }

    @Transactional
    public void delete(String id) {
        StudioSkill s = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Skill 不存在"));
        repository.delete(s);
        configVersionService.bump();
    }

    private String require(Map<String, Object> body, String field) {
        Object v = body.get(field);
        if (v == null || String.valueOf(v).isEmpty()) {
            throw new BusinessException(field + " 不能为空");
        }
        return String.valueOf(v);
    }
}
