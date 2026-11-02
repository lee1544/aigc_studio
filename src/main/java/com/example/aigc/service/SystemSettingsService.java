package com.example.aigc.service;

import com.example.aigc.entity.ops.SystemSetting;
import com.example.aigc.exception.BusinessException;
import com.example.aigc.repository.SystemSettingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 平台参数管理。settings 不含 features（账号权限已剥离到 feature 权限）。
 * 字段与范围按 OpenAPI Settings schema 校验；未初始化时返回产品默认值；任何有效保存都 bump 配置版本。
 */
@Service
public class SystemSettingsService {

    /** OpenAPI Settings 的全部已知字段。 */
    private static final Set<String> FIELDS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "platformName", "allowRegistration", "defaultVideoResolution", "maxVideoDuration",
            "taskConcurrency", "jwtHours", "retentionDays", "autoCleanup")));

    private static final List<String> VIDEO_RESOLUTIONS = Arrays.asList("480P", "720P", "1080P");
    private static final List<Integer> MAX_VIDEO_DURATIONS = Arrays.asList(5, 10, 15, 30);
    private static final List<Integer> JWT_HOURS = Arrays.asList(24, 168);

    @Autowired private SystemSettingRepository repository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ConfigVersionService configVersionService;

    /** 未初始化时使用的产品默认值（对齐 J04 契约示例）。 */
    public JsonNode defaults() {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("platformName", "AI 创作平台");
        n.put("allowRegistration", false);
        n.put("defaultVideoResolution", "480P");
        n.put("maxVideoDuration", 15);
        n.put("taskConcurrency", 4);
        n.put("jwtHours", 24);
        n.put("retentionDays", 30);
        n.put("autoCleanup", false);
        return n;
    }

    /** 管理端读取：默认值 + 已保存值，不含 features。 */
    public JsonNode get() {
        ObjectNode node = (ObjectNode) defaults();
        JsonNode stored = parse(current().getData());
        if (stored != null && stored.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = stored.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                node.set(e.getKey(), e.getValue());
            }
        }
        node.remove("features");
        return node;
    }

    /** 公开端：读取平台参数，不含账号权限。 */
    public JsonNode publicSettings() {
        return get();
    }

    /**
     * 自助注册开关。与 GET /api/public/settings 读同一份 system_settings（id=1），
     * 前端隐藏入口不能替代服务端拦截；未配置时按产品默认值（关闭）处理。
     */
    public boolean isRegistrationAllowed() {
        return get().path("allowRegistration").asBoolean(false);
    }

    @Transactional
    public JsonNode put(JsonNode data) {
        if (data == null || !data.isObject()) {
            throw new BusinessException("settings 必须为对象");
        }
        // 以当前生效值为基底，未传字段保留
        ObjectNode target = (ObjectNode) get().deepCopy();
        Iterator<Map.Entry<String, JsonNode>> it = data.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String key = e.getKey();
            if ("features".equals(key)) {
                continue; // 旧客户端附带 features 忽略，不产生平台级功能开关
            }
            if (!FIELDS.contains(key)) {
                throw new BusinessException("不支持的设置字段: " + key);
            }
            target.set(key, validate(key, e.getValue()));
        }
        SystemSetting s = current();
        s.setData(target.toString());
        repository.save(s);
        configVersionService.bump();
        return get();
    }

    private JsonNode validate(String key, JsonNode value) {
        switch (key) {
            case "platformName":
                return text(value, key, 1, 60);
            case "allowRegistration":
            case "autoCleanup":
                if (value == null || !value.isBoolean()) {
                    throw new BusinessException(key + " 必须为布尔值");
                }
                return value;
            case "defaultVideoResolution":
                return oneOfText(value, key, VIDEO_RESOLUTIONS);
            case "maxVideoDuration":
                return oneOfInt(value, key, MAX_VIDEO_DURATIONS);
            case "taskConcurrency":
                return intBetween(value, key, 1, 20);
            case "jwtHours":
                return oneOfInt(value, key, JWT_HOURS);
            case "retentionDays":
                return intBetween(value, key, 1, 365);
            default:
                throw new BusinessException("不支持的设置字段: " + key);
        }
    }

    private JsonNode text(JsonNode value, String key, int min, int max) {
        if (value == null || !value.isTextual()) {
            throw new BusinessException(key + " 必须为字符串");
        }
        String v = value.asText();
        if (v.length() < min || v.length() > max) {
            throw new BusinessException(key + " 长度必须在 " + min + "-" + max + " 之间");
        }
        return value;
    }

    private JsonNode oneOfText(JsonNode value, String key, List<String> allowed) {
        if (value == null || !value.isTextual() || !allowed.contains(value.asText())) {
            throw new BusinessException(key + " 只能是 " + allowed);
        }
        return value;
    }

    private JsonNode oneOfInt(JsonNode value, String key, List<Integer> allowed) {
        if (value == null || !value.isIntegralNumber() || !allowed.contains(value.asInt())) {
            throw new BusinessException(key + " 只能是 " + allowed);
        }
        return value;
    }

    private JsonNode intBetween(JsonNode value, String key, int min, int max) {
        if (value == null || !value.isIntegralNumber() || value.asInt() < min || value.asInt() > max) {
            throw new BusinessException(key + " 必须在 " + min + "-" + max + " 之间");
        }
        return value;
    }

    private SystemSetting current() {
        return repository.findById(1).orElseGet(() -> {
            SystemSetting s = new SystemSetting();
            s.setId(1);
            s.setData("{}");
            return repository.save(s);
        });
    }

    private JsonNode parse(String json) {
        try { return objectMapper.readTree(json == null ? "{}" : json); }
        catch (Exception e) { return objectMapper.createObjectNode(); }
    }
}
