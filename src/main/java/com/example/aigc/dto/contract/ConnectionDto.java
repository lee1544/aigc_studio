package com.example.aigc.dto.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/** GET /api/admin/model-connections 返回（密钥脱敏，仅 hasKey）。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConnectionDto {

    private String id;
    private String name;
    private String provider;
    private String baseUrl;

    @JsonProperty("hasKey")
    private Boolean hasKey;

    /** 关联模型列表（feature/model/enabled）。 */
    private List<ModelRef> models;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ModelRef {
        private String feature;
        private String model;
        private Integer enabled;
    }
}
