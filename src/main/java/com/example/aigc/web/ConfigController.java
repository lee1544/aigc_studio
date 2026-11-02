package com.example.aigc.web;

import com.example.aigc.service.AgentConfigService;
import com.example.aigc.service.WorkflowConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    public Map<String, Object> saveShortDrama(@RequestBody Map<String, Object> body) {
        return agentConfigService.save(AgentConfigService.SHORT_DRAMA, body);
    }

    @PutMapping("/api/admin/agent/comic/config")
    public Map<String, Object> saveComic(@RequestBody Map<String, Object> body) {
        return agentConfigService.save(AgentConfigService.COMIC, body);
    }

    @PutMapping("/api/admin/agent/canvas/config")
    public Map<String, Object> saveCanvas(@RequestBody Map<String, Object> body) {
        return agentConfigService.save(AgentConfigService.CANVAS, body);
    }

    // ---------- admin 工作流 CRUD（J06）----------

    @GetMapping("/api/admin/workflows")
    public List<Map<String, Object>> listWorkflows() {
        return workflowConfigService.list();
    }

    @PostMapping("/api/admin/workflows")
    public Map<String, Object> createWorkflow(@RequestBody Map<String, Object> body) {
        return workflowConfigService.create(body);
    }

    @PutMapping("/api/admin/workflows/{id}")
    public Map<String, Object> updateWorkflow(@PathVariable String id, @RequestBody Map<String, Object> body) {
        return workflowConfigService.update(id, body);
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
