package com.example.aigc.dto.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** PUT /api/admin/model-configs/{feature}/{model}：完整更新。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelUpdateRequest {

    private String adapter;
    private String endpoint;
    /** 空串沿用旧密钥；读取不回显。 */
    private String apiKey;
    /** null 清除显式绑定。 */
    private String connectionId;
    /** 仅 true 为启用，未传也会停用。 */
    private Boolean enabled;
    private String requestTemplate;
}
