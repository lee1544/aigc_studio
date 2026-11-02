package com.example.aigc.web;

import com.example.aigc.entity.project.Project;
import com.example.aigc.service.ProjectService;
import com.example.aigc.utils.UserContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 本人项目：/api/projects。真实持久化，按当前登录用户过滤，不信任 body.userId。
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Autowired
    private ProjectService projectService;

    @GetMapping
    public List<Project> list() {
        return projectService.list(UserContextUtil.getCurrentUserId());
    }

    @PostMapping
    public Project create(@RequestBody Map<String, Object> body) {
        return projectService.create(UserContextUtil.getCurrentUserId(), body);
    }

    @GetMapping("/{id}/content")
    public Map<String, Object> content(@PathVariable Integer id) {
        return projectService.content(UserContextUtil.getCurrentUserId(), id);
    }

    @PutMapping("/{id}/content")
    public Map<String, Object> saveContent(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        return projectService.saveContent(UserContextUtil.getCurrentUserId(), id, body);
    }

    @PatchMapping("/{id}/module")
    public Project patchModule(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        return projectService.patchModule(UserContextUtil.getCurrentUserId(), id,
                body.get("module") == null ? null : String.valueOf(body.get("module")));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id) {
        projectService.delete(UserContextUtil.getCurrentUserId(), id);
    }
}
