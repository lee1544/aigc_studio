package com.example.aigc.service;

import com.example.aigc.entity.config.ComfyWorkflow;
import com.example.aigc.exception.BusinessException;
import com.example.aigc.exception.NotFoundException;
import com.example.aigc.repository.ComfyWorkflowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 工作流配置 CRUD（/api/admin/workflows）。Java 持久化配置；运行/任务/输出归 Node。
 * graph/mediaBindings 响应为解析后的对象；浏览器仅 hasKey，服务端密钥只从内部配置接口返回。
 * DELETE 仅停用返回 {ok:true}，不删本地运行任务/文件。
 */
@Service
public class WorkflowConfigService {

    @Autowired private ComfyWorkflowRepository repository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ConfigVersionService configVersionService;

    public List<Map<String, Object>> list() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    /** 分页版本；响应体按页收敛，避免 graph 过大时一次性返回全部工作流。 */
    public Page<Map<String, Object>> list(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return repository.findAll(pageRequest).map(this::toView);
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        ComfyWorkflow w = new ComfyWorkflow();
        w.setId(UUID.randomUUID().toString().replace("-", ""));
        applyBody(w, body, true);
        w.setCreatedAt(LocalDateTime.now());
        w.setUpdatedAt(LocalDateTime.now());
        repository.save(w);
        configVersionService.bump();
        return toView(repository.findById(w.getId()).orElse(w));
    }

    @Transactional
    public Map<String, Object> update(String id, Map<String, Object> body) {
        ComfyWorkflow w = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("工作流不存在"));
        applyBody(w, body, false);
        w.setUpdatedAt(LocalDateTime.now());
        repository.save(w);
        configVersionService.bump();
        return toView(w);
    }

    /** DELETE 仅停用，返回 {ok:true}。 */
    @Transactional
    public Map<String, Object> delete(String id) {
        ComfyWorkflow w = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("工作流不存在"));
        w.setEnabled(0);
        w.setUpdatedAt(LocalDateTime.now());
        repository.save(w);
        configVersionService.bump();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        return m;
    }

    /**
     * 仅做配置结构校验，Java 不做真实连通检测，因此返回 WorkflowCheck 的 configurationOnly 分支：
     * {ok:true, configurationOnly:true, message}。id/passed 为兼容旧前端的附加字段。
     * 配置不完整时抛业务异常（明确错误），不固定返回成功。
     */
    public Map<String, Object> check(String id) {
        ComfyWorkflow w = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("工作流不存在"));
        requireNonEmpty(w.getName(), "name");
        requireNonEmpty(w.getBaseUrl(), "baseUrl");
        requireNonEmpty(w.getGraph(), "graph");
        requireNonEmpty(w.getPromptNode(), "promptNode");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("configurationOnly", true);
        m.put("message", "配置校验通过，未执行真实生成");
        m.put("id", id);
        m.put("passed", true);
        return m;
    }

    private void applyBody(ComfyWorkflow w, Map<String, Object> body, boolean isCreate) {
        if (body.get("name") != null) w.setName(String.valueOf(body.get("name")));
        if (body.get("description") != null) w.setDescription(String.valueOf(body.get("description")));
        if (body.get("baseUrl") != null) w.setBaseUrl(String.valueOf(body.get("baseUrl")));
        if (body.get("provider") != null) w.setProvider(String.valueOf(body.get("provider")));
        if (body.get("purpose") != null) w.setPurpose(String.valueOf(body.get("purpose")));
        if (body.get("remoteWorkflowId") != null) w.setRemoteWorkflowId(String.valueOf(body.get("remoteWorkflowId")));
        if (body.get("promptNode") != null) w.setPromptNode(String.valueOf(body.get("promptNode")));
        if (body.get("promptInput") != null) w.setPromptInput(String.valueOf(body.get("promptInput")));
        if (body.get("graph") != null) {
            Object g = body.get("graph");
            w.setGraph(g instanceof String ? (String) g : toJson(g));
        }
        if (body.get("mediaBindings") != null) {
            Object mb = body.get("mediaBindings");
            w.setMediaBindings(mb instanceof String ? (String) mb : toJson(mb));
        }
        // apiKey：空串沿用旧值（更新时），创建时允许空
        if (body.get("apiKey") != null) {
            String k = String.valueOf(body.get("apiKey"));
            if (!k.isEmpty()) {
                w.setApiKey(k);
            }
        }
        if (body.get("enabled") != null) {
            w.setEnabled(Boolean.TRUE.equals(body.get("enabled")) ? 1 : 0);
        }
        if (isCreate) {
            requireNonEmpty(w.getName(), "name");
            requireNonEmpty(w.getBaseUrl(), "baseUrl");
            if (w.getGraph() == null) w.setGraph("{}");
            if (w.getPromptNode() == null) w.setPromptNode("");
        }
    }

    private Map<String, Object> toView(ComfyWorkflow w) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", w.getId());
        m.put("name", w.getName());
        m.put("description", w.getDescription());
        m.put("provider", w.getProvider());
        m.put("workflowId", w.getRemoteWorkflowId());
        m.put("purpose", w.getPurpose());
        m.put("baseUrl", w.getBaseUrl());
        m.put("hasKey", w.getApiKey() != null && !w.getApiKey().isEmpty());
        m.put("graph", parse(w.getGraph()));
        m.put("promptNode", w.getPromptNode());
        m.put("promptInput", w.getPromptInput());
        m.put("mediaBindings", parse(w.getMediaBindings()));
        m.put("enabled", Integer.valueOf(1).equals(w.getEnabled()));
        return m;
    }

    private Object parse(String json) {
        try { return objectMapper.readTree(json == null ? "{}" : json); }
        catch (Exception e) { return json; }
    }

    private String toJson(Object o) {
        try { return objectMapper.writeValueAsString(o); } catch (Exception e) { return "{}"; }
    }

    private void requireNonEmpty(String v, String field) {
        if (v == null || v.isEmpty()) throw new BusinessException(field + " 不能为空");
    }
}
