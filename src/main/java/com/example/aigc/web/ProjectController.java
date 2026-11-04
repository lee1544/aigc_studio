package com.example.aigc.web;

import com.example.aigc.dto.contract.ProjectContentRequest;
import com.example.aigc.dto.contract.ProjectCreateRequest;
import com.example.aigc.dto.contract.ProjectModuleRequest;
import com.example.aigc.entity.project.Project;
import com.example.aigc.service.ProjectService;
import com.example.aigc.utils.UserContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
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
    public Object list(@RequestParam(name = "page", required = false) Integer page,
                       @RequestParam(name = "size", required = false) Integer size) {
        Integer userId = UserContextUtil.getCurrentUserId();
        // 不传分页参数时保持原契约（返回数组）；传入后返回 {content,totalElements,...}
        if (page == null && size == null) {
            return projectService.list(userId);
        }
        int safePage = page == null ? 0 : Math.max(page, 0);
        int safeSize = size == null ? 20 : Math.min(Math.max(size, 1), 200);
        return projectService.list(userId, safePage, safeSize);
    }

    @PostMapping
    public Project create(@Valid @RequestBody ProjectCreateRequest request) {
        return projectService.create(UserContextUtil.getCurrentUserId(), request);
    }

    @GetMapping("/{id}/content")
    public Map<String, Object> content(@PathVariable Integer id) {
        return projectService.content(UserContextUtil.getCurrentUserId(), id);
    }

    @PutMapping("/{id}/content")
    public Map<String, Object> saveContent(@PathVariable Integer id,
                                           @RequestBody ProjectContentRequest request) {
        return projectService.saveContent(UserContextUtil.getCurrentUserId(), id, request);
    }

    @PatchMapping("/{id}/module")
    public Project patchModule(@PathVariable Integer id,
                               @Valid @RequestBody ProjectModuleRequest request) {
        return projectService.patchModule(UserContextUtil.getCurrentUserId(), id, request.getModule());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id) {
        projectService.delete(UserContextUtil.getCurrentUserId(), id);
    }
}
