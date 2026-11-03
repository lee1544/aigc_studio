package com.example.aigc.service;

import com.example.aigc.constant.ModelAdapterCatalog;
import com.example.aigc.dto.contract.ConnectionDto;
import com.example.aigc.dto.contract.ConnectionWriteRequest;
import com.example.aigc.dto.contract.ModelConfigDto;
import com.example.aigc.dto.contract.ModelCreateRequest;
import com.example.aigc.dto.contract.ModelUpdateRequest;
import com.example.aigc.entity.model.DeletedModelConfig;
import com.example.aigc.entity.model.ModelConfig;
import com.example.aigc.entity.model.ModelConnection;
import com.example.aigc.entity.model.ModelConnectionCheck;
import com.example.aigc.exception.BusinessException;
import com.example.aigc.exception.ConflictException;
import com.example.aigc.exception.NotFoundException;
import com.example.aigc.repository.DeletedModelConfigRepository;
import com.example.aigc.repository.ModelConfigRepository;
import com.example.aigc.repository.ModelConnectionCheckRepository;
import com.example.aigc.repository.ModelConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 服务连接与模型配置管理（/api/admin/model-connections、/api/admin/model-configs、
 * /api/admin/model-connection-checks、POST /model-connections/{id}/check）。
 */
@Service
public class ModelConnectionService {

    private static final Logger log = LoggerFactory.getLogger(ModelConnectionService.class);
    private static final int MAX_CUSTOM_PER_FEATURE = 50;

    @Autowired private ModelConnectionRepository connectionRepository;
    @Autowired private ModelConfigRepository modelConfigRepository;
    @Autowired private DeletedModelConfigRepository deletedModelConfigRepository;
    @Autowired private ModelConnectionCheckRepository checkRepository;
    @Autowired private ConfigVersionService configVersionService;

    private final RestTemplate restTemplate;

    public ModelConnectionService(RestTemplateBuilder restTemplateBuilder) {
        // 外部厂商接口不可控：必须设置连接/读取超时，避免检测请求无限挂起
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ---------- 连接 ----------

    public List<ConnectionDto> listConnections() {
        Map<String, List<ModelConfig>> modelsByConnection = new HashMap<>();
        for (ModelConfig mc : modelConfigRepository.findAll()) {
            if (mc.getConnectionId() != null) {
                modelsByConnection.computeIfAbsent(mc.getConnectionId(), key -> new ArrayList<>()).add(mc);
            }
        }
        return connectionRepository.findAll().stream()
                .map(c -> toConnectionDto(c, modelsByConnection.getOrDefault(c.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    @Transactional
    public ConnectionDto createConnection(ConnectionWriteRequest req) {
        if (req.getApiKey() == null || req.getApiKey().isEmpty() || req.getApiKey().contains("\n")) {
            throw new BusinessException("创建连接必须提供有效 apiKey");
        }
        ModelConnection c = new ModelConnection();
        c.setId(UUID.randomUUID().toString());
        c.setName(req.getName());
        c.setProvider(req.getProvider());
        c.setBaseUrl(req.getBaseUrl());
        c.setApiKey(req.getApiKey());
        connectionRepository.save(c);
        configVersionService.bump();
        return toConnectionDto(c, Collections.emptyList());
    }

    @Transactional
    public ConnectionDto updateConnection(String id, ConnectionWriteRequest req) {
        ModelConnection c = connectionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("连接不存在"));
        c.setName(req.getName());
        c.setProvider(req.getProvider());
        c.setBaseUrl(req.getBaseUrl());
        if (req.getApiKey() != null && !req.getApiKey().isEmpty()) {
            if (req.getApiKey().contains("\n")) {
                throw new BusinessException("apiKey 禁止换行");
            }
            c.setApiKey(req.getApiKey());
        }
        connectionRepository.save(c);
        // 关联模型只在自身没有端点时回填连接地址；密钥不复制进模型，
        // 由 hasEffectiveKey()/快照在读取时回退到连接密钥，避免连接轮换后留下过期副本。
        List<ModelConfig> related = modelConfigRepository.findByConnectionId(id);
        for (ModelConfig mc : related) {
            if (mc.getEndpoint() == null || mc.getEndpoint().isEmpty()) {
                mc.setEndpoint(c.getBaseUrl());
            }
            modelConfigRepository.save(mc);
        }
        // 密钥或地址变更后清除旧检测结果
        checkRepository.findById(id).ifPresent(checkRepository::delete);
        configVersionService.bump();
        return toConnectionDto(c, related);
    }

    @Transactional
    public void deleteConnection(String id) {
        ModelConnection c = connectionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("连接不存在"));
        if (!modelConfigRepository.findByConnectionId(id).isEmpty()) {
            throw new ConflictException("连接被模型引用，不能删除");
        }
        connectionRepository.delete(c);
        checkRepository.findById(id).ifPresent(checkRepository::delete);
        configVersionService.bump();
    }

    /** POST /model-connections/{id}/check：只请求 /v1/models 目录，不生成。 */
    public ModelConnectionCheck checkConnection(String id) {
        ModelConnection c = connectionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("连接不存在"));
        long start = System.currentTimeMillis();
        String status;
        String message;
        try {
            HttpHeaders headers = new HttpHeaders();
            if (c.getApiKey() != null && !c.getApiKey().isEmpty()) {
                headers.setBearerAuth(c.getApiKey());
            }
            String url = joinUrl(c.getBaseUrl(), "/v1/models");
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            int code = resp.getStatusCodeValue();
            if (code >= 200 && code < 300) {
                status = "passed";
                message = "目录认证通过";
            } else if (code == 401 || code == 403) {
                status = "failed";
                message = "认证失败";
            } else {
                status = "unsupported";
                message = "服务未提供目录接口";
            }
        } catch (HttpStatusCodeException e) {
            // RestTemplate 对 4xx/5xx 抛异常，必须在 catch 中区分认证失败，否则一律误报 unsupported
            int code = e.getRawStatusCode();
            if (code == 401 || code == 403) {
                status = "failed";
                message = "认证失败";
            } else if (code == 404) {
                status = "unsupported";
                message = "服务未提供目录接口";
            } else {
                status = "failed";
                message = "目录检测返回 HTTP " + code;
            }
        } catch (Exception e) {
            status = "unsupported";
            // 不把内部异常信息（可能含主机/端口）回显给调用方
            message = "请求失败，请检查端点与网络";
            log.warn("连接检测失败 id={}: {}", id, e.getMessage(), e);
        }
        int latency = (int) (System.currentTimeMillis() - start);

        ModelConnectionCheck check = checkRepository.findById(id).orElse(new ModelConnectionCheck());
        check.setConnectionId(id);
        check.setCheckedAt(LocalDateTime.now());
        check.setStatus(status);
        check.setMessage(message);
        check.setLatencyMs(latency);
        return checkRepository.save(check);
    }

    public List<ModelConnectionCheck> listChecks() {
        return checkRepository.findAll();
    }

    // ---------- 模型配置 ----------

    public List<ModelConfigDto> listModelConfigs() {
        Map<String, ModelConnection> connections = new HashMap<>();
        for (ModelConnection c : connectionRepository.findAll()) {
            connections.put(c.getId(), c);
        }
        return modelConfigRepository.findAll().stream()
                .map(mc -> toModelConfigDto(mc, connections))
                .collect(Collectors.toList());
    }

    @Transactional
    public ModelConfigDto createModelConfig(ModelCreateRequest req) {
        if (!ModelAdapterCatalog.supportsFeature(req.getFeature())) {
            throw new BusinessException("不支持的 feature: " + req.getFeature());
        }
        if (!ModelAdapterCatalog.supports(req.getFeature(), req.getAdapter())) {
            throw new BusinessException("feature " + req.getFeature() + " 不支持适配器 " + req.getAdapter()
                    + "，可选适配器: " + ModelAdapterCatalog.adaptersOf(req.getFeature()));
        }
        ModelConfig.PK pk = new ModelConfig.PK();
        pk.setFeature(req.getFeature());
        pk.setModel(req.getModel());
        if (modelConfigRepository.existsById(pk)) {
            throw new BusinessException("模型配置已存在");
        }
        if (deletedModelConfigRepository.existsByFeatureAndModel(req.getFeature(), req.getModel())) {
            throw new BusinessException("该模型已被删除，不能直接复活");
        }
        long count = modelConfigRepository.countByFeatureAndCustom(req.getFeature(), 1);
        if (count >= MAX_CUSTOM_PER_FEATURE) {
            throw new BusinessException("每分类最多 " + MAX_CUSTOM_PER_FEATURE + " 个自定义模型");
        }
        ModelConfig mc = new ModelConfig();
        mc.setFeature(req.getFeature());
        mc.setModel(req.getModel());
        mc.setProvider(req.getProvider());
        mc.setAdapter(req.getAdapter());
        mc.setEndpoint(req.getEndpoint() == null ? "" : req.getEndpoint());
        mc.setApiKey("");
        mc.setEnabled(0);
        mc.setCustom(1);
        mc.setRequestTemplate(req.getRequestTemplate() == null ? "" : req.getRequestTemplate());
        mc.setConnectionId(null);
        modelConfigRepository.save(mc);
        configVersionService.bump();
        return toModelConfigDto(mc);
    }

    @Transactional
    public ModelConfigDto updateModelConfig(String feature, String model, ModelUpdateRequest req) {
        ModelConfig.PK pk = new ModelConfig.PK();
        pk.setFeature(feature);
        pk.setModel(model);
        ModelConfig mc = modelConfigRepository.findById(pk)
                .orElseThrow(() -> new NotFoundException("模型配置不存在"));
        if (req.getAdapter() != null) {
            if (!ModelAdapterCatalog.supports(feature, req.getAdapter())) {
                throw new BusinessException("feature " + feature + " 不支持适配器 " + req.getAdapter()
                        + "，可选适配器: " + ModelAdapterCatalog.adaptersOf(feature));
            }
            mc.setAdapter(req.getAdapter());
        }
        if (req.getEndpoint() != null) mc.setEndpoint(req.getEndpoint());
        if (req.getRequestTemplate() != null) mc.setRequestTemplate(req.getRequestTemplate());
        if (req.getApiKey() != null && !req.getApiKey().isEmpty()) {
            mc.setApiKey(req.getApiKey());
        }
        if (req.getConnectionId() != null) {
            String connectionId = req.getConnectionId().isEmpty() ? null : req.getConnectionId();
            if (connectionId != null && !connectionRepository.findById(connectionId).isPresent()) {
                throw new NotFoundException("连接不存在");
            }
            mc.setConnectionId(connectionId);
        }
        // enabled 仅 true 为启用，未传也会停用
        mc.setEnabled(Boolean.TRUE.equals(req.getEnabled()) ? 1 : 0);
        if (Integer.valueOf(1).equals(mc.getEnabled())) {
            requireExecutable(mc);
        }
        modelConfigRepository.save(mc);
        configVersionService.bump();
        return toModelConfigDto(mc);
    }

    /** 启用前必须具备：受支持适配器 + 端点 + 有效密钥（自身或绑定连接），仅填 endpoint 不算可用。 */
    private void requireExecutable(ModelConfig mc) {
        if (!ModelAdapterCatalog.supports(mc.getFeature(), mc.getAdapter())) {
            throw new BusinessException("启用前必须选择受支持的适配器，可选适配器: "
                    + ModelAdapterCatalog.adaptersOf(mc.getFeature()));
        }
        if (mc.getEndpoint() == null || mc.getEndpoint().isEmpty()) {
            throw new BusinessException("启用前必须补齐 endpoint");
        }
        if (!hasEffectiveKey(mc)) {
            throw new BusinessException("启用前必须配置有效密钥");
        }
    }

    private boolean hasEffectiveKey(ModelConfig mc) {
        return hasEffectiveKey(mc, null);
    }

    private boolean hasEffectiveKey(ModelConfig mc, Map<String, ModelConnection> connections) {
        if (mc.getApiKey() != null && !mc.getApiKey().isEmpty()) return true;
        if (mc.getConnectionId() == null || mc.getConnectionId().isEmpty()) return false;
        ModelConnection c = connections != null
                ? connections.get(mc.getConnectionId())
                : connectionRepository.findById(mc.getConnectionId()).orElse(null);
        return c != null && c.getApiKey() != null && !c.getApiKey().isEmpty();
    }

    @Transactional
    public void deleteModelConfig(String feature, String model) {
        ModelConfig.PK pk = new ModelConfig.PK();
        pk.setFeature(feature);
        pk.setModel(model);
        ModelConfig mc = modelConfigRepository.findById(pk)
                .orElseThrow(() -> new NotFoundException("模型配置不存在"));
        modelConfigRepository.delete(mc);
        DeletedModelConfig del = new DeletedModelConfig();
        del.setFeature(feature);
        del.setModel(model);
        deletedModelConfigRepository.save(del);
        configVersionService.bump();
    }

    // ---------- 映射 ----------

    private ConnectionDto toConnectionDto(ModelConnection c, List<ModelConfig> models) {
        ConnectionDto dto = new ConnectionDto();
        dto.setId(c.getId());
        dto.setName(c.getName());
        dto.setProvider(c.getProvider());
        dto.setBaseUrl(c.getBaseUrl());
        dto.setHasKey(c.getApiKey() != null && !c.getApiKey().isEmpty());
        List<ConnectionDto.ModelRef> refs = models.stream()
                .map(mc -> {
                    ConnectionDto.ModelRef r = new ConnectionDto.ModelRef();
                    r.setFeature(mc.getFeature());
                    r.setModel(mc.getModel());
                    r.setEnabled(mc.getEnabled());
                    return r;
                })
                .collect(Collectors.toList());
        dto.setModels(refs);
        return dto;
    }

    private ModelConfigDto toModelConfigDto(ModelConfig mc) {
        return toModelConfigDto(mc, null);
    }

    private ModelConfigDto toModelConfigDto(ModelConfig mc, Map<String, ModelConnection> connections) {
        ModelConfigDto dto = new ModelConfigDto();
        dto.setConnectionId(mc.getConnectionId());
        dto.setFeature(mc.getFeature());
        dto.setModel(mc.getModel());
        dto.setProvider(mc.getProvider());
        dto.setAdapter(mc.getAdapter());
        dto.setEndpoint(mc.getEndpoint());
        dto.setRequestTemplate(mc.getRequestTemplate());
        dto.setEnabled(Integer.valueOf(1).equals(mc.getEnabled()));
        // hasKey 按“执行时是否拿得到密钥”判定：自身密钥优先，其次来自绑定连接
        dto.setHasKey(hasEffectiveKey(mc, connections));
        dto.setSupported(ModelAdapterCatalog.supports(mc.getFeature(), mc.getAdapter()));
        dto.setCustom(Integer.valueOf(1).equals(mc.getCustom()));
        dto.setModes(Collections.emptyList());
        return dto;
    }

    private String joinUrl(String base, String path) {
        if (base == null) return path;
        if (base.endsWith("/") && path.startsWith("/")) {
            return base + path.substring(1);
        }
        if (!base.endsWith("/") && !path.startsWith("/")) {
            return base + "/" + path;
        }
        return base + path;
    }
}
