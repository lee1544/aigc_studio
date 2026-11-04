package com.example.aigc.service;

import com.example.aigc.dto.contract.StudioSkillCreateRequest;
import com.example.aigc.dto.contract.StudioSkillUpdateRequest;
import com.example.aigc.entity.ops.StudioSkill;
import com.example.aigc.exception.BusinessException;
import com.example.aigc.exception.NotFoundException;
import com.example.aigc.repository.StudioSkillRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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
    public StudioSkill create(StudioSkillCreateRequest request) {
        StudioSkill s = new StudioSkill();
        s.setId(UUID.randomUUID().toString().replace("-", ""));
        s.setName(request.getName());
        s.setDescription(request.getDescription() == null ? "" : request.getDescription());
        s.setContent(request.getContent() == null ? "" : request.getContent());
        s.setEnabled(request.getEnabled() == null || request.getEnabled() ? 1 : 0);
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());
        StudioSkill saved = repository.save(s);
        configVersionService.bump();
        return saved;
    }

    @Transactional
    public StudioSkill update(String id, StudioSkillUpdateRequest request) {
        StudioSkill s = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Skill 不存在"));
        if (request.getName() != null) {
            if (request.getName().trim().isEmpty()) {
                throw new BusinessException("name 不能为空");
            }
            s.setName(request.getName());
        }
        if (request.getDescription() != null) s.setDescription(request.getDescription());
        if (request.getContent() != null) s.setContent(request.getContent());
        if (request.getEnabled() != null) {
            s.setEnabled(request.getEnabled() ? 1 : 0);
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
}
