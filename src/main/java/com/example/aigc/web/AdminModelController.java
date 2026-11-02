package com.example.aigc.web;

import com.example.aigc.dto.contract.ConnectionDto;
import com.example.aigc.dto.contract.ConnectionWriteRequest;
import com.example.aigc.dto.contract.ModelConfigDto;
import com.example.aigc.dto.contract.ModelCreateRequest;
import com.example.aigc.dto.contract.ModelUpdateRequest;
import com.example.aigc.entity.model.ModelConnectionCheck;
import com.example.aigc.service.ModelConnectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 管理端模型连接与配置：/api/admin/model-connections、/api/admin/model-configs、
 * /api/admin/model-connection-checks。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminModelController {

    @Autowired
    private ModelConnectionService modelConnectionService;

    // ---------- 连接 ----------

    @GetMapping("/model-connections")
    public List<ConnectionDto> listConnections() {
        return modelConnectionService.listConnections();
    }

    @PostMapping("/model-connections")
    public ConnectionDto createConnection(@Valid @RequestBody ConnectionWriteRequest req) {
        return modelConnectionService.createConnection(req);
    }

    @PutMapping("/model-connections/{id}")
    public ConnectionDto updateConnection(@PathVariable String id,
                                          @Valid @RequestBody ConnectionWriteRequest req) {
        return modelConnectionService.updateConnection(id, req);
    }

    @DeleteMapping("/model-connections/{id}")
    public void deleteConnection(@PathVariable String id) {
        modelConnectionService.deleteConnection(id);
    }

    @PostMapping("/model-connections/{id}/check")
    public ModelConnectionCheck checkConnection(@PathVariable String id) {
        return modelConnectionService.checkConnection(id);
    }

    @GetMapping("/model-connection-checks")
    public List<ModelConnectionCheck> listChecks() {
        return modelConnectionService.listChecks();
    }

    // ---------- 模型配置 ----------

    @GetMapping("/model-configs")
    public List<ModelConfigDto> listModelConfigs() {
        return modelConnectionService.listModelConfigs();
    }

    @PostMapping("/model-configs")
    public ModelConfigDto createModelConfig(@Valid @RequestBody ModelCreateRequest req) {
        return modelConnectionService.createModelConfig(req);
    }

    @PutMapping("/model-configs/{feature}/{model}")
    public ModelConfigDto updateModelConfig(@PathVariable String feature,
                                            @PathVariable String model,
                                            @Valid @RequestBody ModelUpdateRequest req) {
        return modelConnectionService.updateModelConfig(feature, model, req);
    }

    @DeleteMapping("/model-configs/{feature}/{model}")
    public void deleteModelConfig(@PathVariable String feature,
                                  @PathVariable String model) {
        modelConnectionService.deleteModelConfig(feature, model);
    }
}
