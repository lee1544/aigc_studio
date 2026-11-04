package com.example.aigc.dto.contract;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import javax.validation.constraints.Size;

/**
 * POST/PUT /api/admin/workflows：创建或部分更新工作流配置。
 * 创建时 name/baseUrl 仍由服务端校验必填；更新时 null 表示不修改。
 * graph/mediaBindings 既接受 JSON 对象，也接受已序列化的字符串（字符串按原样保存）。
 */
@Data
public class WorkflowWriteRequest {

    @Size(max = 100, message = "name 长度不能超过 100 个字符")
    private String name;

    private String description;

    @Size(max = 255, message = "baseUrl 长度不能超过 255 个字符")
    private String baseUrl;

    @Size(max = 32, message = "provider 长度不能超过 32 个字符")
    private String provider;

    @Size(max = 32, message = "purpose 长度不能超过 32 个字符")
    private String purpose;

    @Size(max = 64, message = "remoteWorkflowId 长度不能超过 64 个字符")
    private String remoteWorkflowId;

    @Size(max = 64, message = "promptNode 长度不能超过 64 个字符")
    private String promptNode;

    @Size(max = 64, message = "promptInput 长度不能超过 64 个字符")
    private String promptInput;

    private JsonNode graph;

    private JsonNode mediaBindings;

    /** 省略或空串时沿用旧密钥；创建时允许为空。 */
    @Size(max = 8192, message = "apiKey 长度不能超过 8192 个字符")
    private String apiKey;

    private Boolean enabled;
}
