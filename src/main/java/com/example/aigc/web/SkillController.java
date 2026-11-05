package com.example.aigc.web;

import com.example.aigc.dto.contract.StudioSkillCreateRequest;
import com.example.aigc.dto.contract.StudioSkillUpdateRequest;
import com.example.aigc.entity.ops.StudioSkill;
import com.example.aigc.service.StudioSkillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * Skill 管理 /api/admin/studio-skills 与公开 /api/studio-skills。
 * <p>公开列表保持开放；管理端 CRUD 属于平台级全局配置，管理员（含组织管理员）均可维护。
 */
@RestController
public class SkillController {

    @Autowired
    private StudioSkillService studioSkillService;

    @GetMapping("/api/studio-skills")
    public List<StudioSkill> publicList() {
        return studioSkillService.listEnabled();
    }

    @GetMapping("/api/admin/studio-skills")
    public List<StudioSkill> list() {
        return studioSkillService.list();
    }

    @PostMapping("/api/admin/studio-skills")
    public StudioSkill create(@Valid @RequestBody StudioSkillCreateRequest request) {
        return studioSkillService.create(request);
    }

    @PutMapping("/api/admin/studio-skills/{id}")
    public StudioSkill update(@PathVariable String id, @Valid @RequestBody StudioSkillUpdateRequest request) {
        return studioSkillService.update(id, request);
    }

    @DeleteMapping("/api/admin/studio-skills/{id}")
    public void delete(@PathVariable String id) {
        studioSkillService.delete(id);
    }
}
