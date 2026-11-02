package com.example.aigc.dto.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** POST/PUT /api/admin/model-connections。 */
@Data
public class ConnectionWriteRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    @Size(max = 100)
    private String provider;

    @NotBlank
    private String baseUrl;

    /** 创建必填；更新省略或空串保留旧密钥。禁止换行。 */
    @Size(max = 8192)
    private String apiKey;
}
