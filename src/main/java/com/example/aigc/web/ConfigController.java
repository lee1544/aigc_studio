package com.example.aigc.web;

import com.example.aigc.dto.contract.AgentConfigRequest;
import com.example.aigc.dto.contract.WorkflowWriteRequest;
import com.example.aigc.service.AgentConfigService;
import com.example.aigc.service.WorkflowConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

/**
 * Java 归属的配置接口：Agent 配置（J05）与 admin 工作流 CRUD（J06）。
 * 执行类路径（text-studio/workflows run/music/canvas/digital-humans/autodl/模型目录）
 * 由 NodeOwnedController 统一返回 503，归 Node 服务。
 */
@RestController
public class ConfigController {

    @Autowired
    private AgentConfigService agentConfigService;

    @Autowired
    private WorkflowConfigService workflowConfigService;

    // ---------- Agent 配置（J05）----------

    @GetMapping("/api/agent/short-drama/config")
    public Map<String, Object> shortDramaConfig() {
        return agentConfigService.load(AgentConfigService.SHORT_DRAMA);
    }

    @GetMapping("/api/agent/comic/config")
    public Map<String, Object> comicConfig() {
        return agentConfigService.load(AgentConfigService.COMIC);
    }

    @GetMapping("/api/agent/canvas/config")
    public Map<String, Object> canvasConfig() {
        return agentConfigService.load(AgentConfigService.CANVAS);
    }

    @PutMapping("/api/admin/agent/short-drama/config")
    public Map<String, Object> saveShortDrama(@Valid @RequestBody AgentConfigRequest request) {
        return agentConfigService.save(AgentConfigService.SHORT_DRAMA, request);
    }

    @PutMapping("/api/admin/agent/comic/config")
    public Map<String, Object> saveComic(@Valid @RequestBody AgentConfigRequest request) {
        return agentConfigService.save(AgentConfigService.COMIC, request);
    }

    @PutMapping("/api/admin/agent/canvas/config")
    public Map<String, Object> saveCanvas(@Valid @RequestBody AgentConfigRequest request) {
        return agentConfigService.save(AgentConfigService.CANVAS, request);
    }

    // ---------- admin 工作流 CRUD（J06）----------

    @GetMapping("/api/admin/workflows")
    public Object listWorkflows(@RequestParam(name = "page", required = false) Integer page,
                                @RequestParam(name = "size", required = false) Integer size) {
        // 不传分页参数时保持原契约（返回数组）
        if (page == null && size == null) {
            return workflowConfigService.list();
        }
        int safePage = page == null ? 0 : Math.max(page, 0);
        int safeSize = size == null ? 20 : Math.min(Math.max(size, 1), 100);
        return workflowConfigService.list(safePage, safeSize);
    }

    @PostMapping("/api/admin/workflows")
    public Map<String, Object> createWorkflow(@Valid @RequestBody WorkflowWriteRequest request) {
        return workflowConfigService.create(request);
    }

    @PutMapping("/api/admin/workflows/{id}")
    public Map<String, Object> updateWorkflow(@PathVariable String id,
                                              @Valid @RequestBody WorkflowWriteRequest request) {
        return workflowConfigService.update(id, request);
    }

    @DeleteMapping("/api/admin/workflows/{id}")
    public Map<String, Object> deleteWorkflow(@PathVariable String id) {
        return workflowConfigService.delete(id);
    }

    @PostMapping("/api/admin/workflows/{id}/check")
    public Map<String, Object> checkWorkflow(@PathVariable String id) {
        return workflowConfigService.check(id);
    }
}
