package com.example.aigc.dto.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** GET /api/admin/model-configs 返回。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelConfigDto {

    @JsonProperty("connectionId")
    private String connectionId;

    private String feature;
    private String model;
    private String provider;
    private String adapter;
    private String endpoint;

    @JsonProperty("requestTemplate")
    private String requestTemplate;

    private Boolean enabled;
    private Boolean hasKey;
    private Boolean supported;
    private Boolean custom;

    private List<String> modes;

    /** 视频能力，按适配器计算；非视频模型可省略。 */
    private Map<String, Object> capabilities;
}
