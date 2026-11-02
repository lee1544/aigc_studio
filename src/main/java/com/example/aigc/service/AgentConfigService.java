package com.example.aigc.service;

import com.example.aigc.entity.config.AgentStepModel;
import com.example.aigc.entity.config.AgentSystemPrompt;
import com.example.aigc.exception.BusinessException;
import com.example.aigc.repository.AgentStepModelRepository;
import com.example.aigc.repository.AgentSystemPromptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 配置持久化（原始选择，不计算有效模型）。
 * shortDrama: script/storyboard/image/video/voice；comic: script/storyboard/image/video；canvas: 单 model。
 */
@Service
public class AgentConfigService {

    public static final String SHORT_DRAMA = "shortDrama";
    public static final String COMIC = "comic";
    public static final String CANVAS = "canvas";

    private static final List<String> SHORT_DRAMA_STEPS = Arrays.asList("script", "storyboard", "image", "video", "voice");
    private static final List<String> COMIC_STEPS = Arrays.asList("script", "storyboard", "image", "video");
    private static final int MAX_PROMPT = 20000;

    @Autowired private AgentStepModelRepository stepRepository;
    @Autowired private AgentSystemPromptRepository promptRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ConfigVersionService configVersionService;

    @Transactional
    public Map<String, Object> save(String agent, Map<String, Object> body) {
        List<String> steps = stepsOf(agent);
        if (steps == null) {
            throw new BusinessException("未知 agent: " + agent);
        }
        // systemPrompt
        String prompt = body.get("systemPrompt") == null ? null : String.valueOf(body.get("systemPrompt")).trim();
        if (prompt != null) {
            if (prompt.length() > MAX_PROMPT) {
                throw new BusinessException("systemPrompt 最多 " + MAX_PROMPT + " 字符");
            }
            AgentSystemPrompt existing = promptRepository.findById(agent).orElse(new AgentSystemPrompt());
            existing.setAgent(agent);
            existing.setSystemPrompt(prompt);
            promptRepository.save(existing);
        }
        // models / model
        if (CANVAS.equals(agent)) {
            String model = body.get("model") == null ? null : String.valueOf(body.get("model"));
            if (model != null) saveStep(agent, "model", model);
        } else {
            Object modelsObj = body.get("models");
            if (modelsObj instanceof Map) {
                Map<?, ?> models = (Map<?, ?>) modelsObj;
                for (String step : steps) {
                    Object v = models.get(step);
                    if (v != null) {
                        saveStep(agent, step, String.valueOf(v));
                    }
                }
            }
        }
        configVersionService.bump();
        return load(agent);
    }

    public Map<String, Object> load(String agent) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("agent", agent);
        AgentSystemPrompt p = promptRepository.findById(agent).orElse(null);
        m.put("systemPrompt", p == null ? "" : p.getSystemPrompt());
        List<AgentStepModel> rows = stepRepository.findByAgent(agent);
        if (CANVAS.equals(agent)) {
            String model = rows.stream().filter(r -> "model".equals(r.getStep()))
                    .map(AgentStepModel::getModel).findFirst().orElse("");
            m.put("selection", model);
            m.put("model", model);
        } else {
            Map<String, String> selections = new LinkedHashMap<>();
            for (String step : stepsOf(agent)) {
                selections.put(step, "");
            }
            for (AgentStepModel r : rows) {
                selections.put(r.getStep(), r.getModel());
            }
            m.put("selections", selections);
            m.put("models", selections);
        }
        return m;
    }

    private void saveStep(String agent, String step, String model) {
        AgentStepModel.PK pk = new AgentStepModel.PK();
        pk.setAgent(agent);
        pk.setStep(step);
        AgentStepModel row = stepRepository.findById(pk).orElse(new AgentStepModel());
        row.setAgent(agent);
        row.setStep(step);
        row.setModel(model == null ? "" : model);
        stepRepository.save(row);
    }

    private List<String> stepsOf(String agent) {
        switch (agent) {
            case SHORT_DRAMA: return SHORT_DRAMA_STEPS;
            case COMIC: return COMIC_STEPS;
            case CANVAS: return java.util.Collections.singletonList("model");
            default: return null;
        }
    }
}
