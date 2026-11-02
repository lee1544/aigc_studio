package com.example.aigc.web;

import com.example.aigc.service.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** 系统设置：PUT /api/admin/system-settings、GET /api/public/settings、GET /api/admin/apis（旧兼容）。 */
@RestController
public class AdminSystemController {

    @Autowired
    private SystemSettingsService systemSettingsService;

    @PutMapping("/api/admin/system-settings")
    public JsonNode putSettings(@RequestBody JsonNode body) {
        return systemSettingsService.put(body);
    }

    @GetMapping("/api/public/settings")
    public JsonNode publicSettings() {
        return systemSettingsService.publicSettings();
    }

    /** 旧 API 配置表只读列表，现以空数组兼容。 */
    @GetMapping("/api/admin/apis")
    public Map<String, Object> legacyApis() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("apis", java.util.Collections.emptyList());
        return m;
    }
}
