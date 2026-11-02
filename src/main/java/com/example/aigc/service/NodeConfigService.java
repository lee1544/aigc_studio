package com.example.aigc.service;

import com.example.aigc.dto.contract.UserMapper;
import com.example.aigc.entity.config.ConfigVersion;
import com.example.aigc.entity.config.ComfyWorkflow;
import com.example.aigc.entity.model.DeletedModelConfig;
import com.example.aigc.entity.model.ModelConfig;
import com.example.aigc.entity.model.ModelConnection;
import com.example.aigc.entity.ops.StudioSkill;
import com.example.aigc.repository.ComfyWorkflowRepository;
import com.example.aigc.repository.DeletedModelConfigRepository;
import com.example.aigc.repository.ModelConfigRepository;
import com.example.aigc.repository.ModelConnectionRepository;
import com.example.aigc.repository.StudioSkillRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 内部配置快照聚合（GET /api/internal/node-config）。
 * 供 Node 服务读取 Java 已保存的非计费配置；包含真实密钥，不含积分/售价/兑换比例。
 */
@Service
public class NodeConfigService {

    @Autowired private ModelConnectionRepository connectionRepository;
    @Autowired private ModelConfigRepository modelConfigRepository;
    @Autowired private DeletedModelConfigRepository deletedModelConfigRepository;
    @Autowired private StudioSkillRepository studioSkillRepository;
    @Autowired private ComfyWorkflowRepository comfyWorkflowRepository;
    @Autowired private AgentConfigService agentConfigService;
    @Autowired private WorkflowConfigService workflowConfigService;
    @Autowired private SystemSettingsService systemSettingsService;
    @Autowired private ConfigVersionService configVersionService;
    @Autowired private ObjectMapper objectMapper;

    /** 只读快照：同一事务内读取，避免半次提交的配置混入。 */
    @Transactional(readOnly = true)
    public Map<String, Object> snapshot() {
        ConfigVersion v = configVersionService.current();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", String.valueOf(v.getVersion()));
        m.put("updatedAt", UserMapper.iso(v.getUpdatedAt()));

        // connections（含真实密钥）
        m.put("connections", connectionRepository.findAll().stream()
                .map(c -> {
                    Map<String, Object> cm = new LinkedHashMap<>();
                    cm.put("id", c.getId());
                    cm.put("name", c.getName());
                    cm.put("provider", c.getProvider());
                    cm.put("baseUrl", c.getBaseUrl());
                    cm.put("apiKey", c.getApiKey() == null ? "" : c.getApiKey());
                    return cm;
                })
                .collect(Collectors.toList()));

        // modelConfigs（含解析后真实密钥、enabled boolean）
        m.put("modelConfigs", modelConfigRepository.findAll().stream()
                .map(this::modelConfigView)
                .collect(Collectors.toList()));

        // agents（原始选择）
        Map<String, Object> agents = new LinkedHashMap<>();
        agents.put("shortDrama", agentConfigService.load(AgentConfigService.SHORT_DRAMA));
        agents.put("comic", agentConfigService.load(AgentConfigService.COMIC));
        agents.put("canvas", agentConfigService.load(AgentConfigService.CANVAS));
        m.put("agents", agents);

        // skills（含停用项）
        List<StudioSkill> skills = studioSkillRepository.findAll();
        m.put("skills", skills.stream().map(this::skillView).collect(Collectors.toList()));

        // workflows（含真实密钥）
        m.put("workflows", comfyWorkflowRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::workflowView)
                .collect(Collectors.toList()));

        // settings
        JsonNode settings = systemSettingsService.get();
        m.put("settings", settings);

        // deletedModelConfigs
        m.put("deletedModelConfigs", deletedModelConfigRepository.findAll().stream()
                .map(d -> {
                    Map<String, Object> dm = new LinkedHashMap<>();
                    dm.put("feature", d.getFeature());
                    dm.put("model", d.getModel());
                    return dm;
                })
                .collect(Collectors.toList()));
        return m;
    }

    private Map<String, Object> modelConfigView(ModelConfig mc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("feature", mc.getFeature());
        m.put("model", mc.getModel());
        m.put("provider", mc.getProvider());
        m.put("adapter", mc.getAdapter());
        m.put("endpoint", mc.getEndpoint());
        m.put("apiKey", effectiveKey(mc));
        m.put("enabled", Integer.valueOf(1).equals(mc.getEnabled()));
        m.put("connectionId", mc.getConnectionId());
        m.put("requestTemplate", mc.getRequestTemplate());
        return m;
    }

    /** 最终有效密钥：自身密钥优先，否则回退绑定连接的密钥；都没有返回空串。 */
    private String effectiveKey(ModelConfig mc) {
        if (mc.getApiKey() != null && !mc.getApiKey().isEmpty()) {
            return mc.getApiKey();
        }
        if (mc.getConnectionId() == null || mc.getConnectionId().isEmpty()) {
            return "";
        }
        ModelConnection c = connectionRepository.findById(mc.getConnectionId()).orElse(null);
        return c == null || c.getApiKey() == null ? "" : c.getApiKey();
    }

    private Map<String, Object> skillView(StudioSkill s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("name", s.getName());
        m.put("description", s.getDescription());
        m.put("content", s.getContent());
        // 沿用原 OpenAPI Skill 字段：enabled 为 0/1，另带创建/更新时间
        m.put("enabled", s.getEnabled());
        m.put("created_at", UserMapper.iso(s.getCreatedAt()));
        m.put("updated_at", UserMapper.iso(s.getUpdatedAt()));
        return m;
    }

    private Map<String, Object> workflowView(ComfyWorkflow w) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", w.getId());
        m.put("name", w.getName());
        m.put("description", w.getDescription());
        m.put("provider", w.getProvider());
        m.put("workflowId", w.getRemoteWorkflowId());
        m.put("purpose", w.getPurpose());
        m.put("baseUrl", w.getBaseUrl());
        m.put("hasKey", w.getApiKey() != null && !w.getApiKey().isEmpty());
        m.put("apiKey", w.getApiKey() == null ? "" : w.getApiKey());
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
}
