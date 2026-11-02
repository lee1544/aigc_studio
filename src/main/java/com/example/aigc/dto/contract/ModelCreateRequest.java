package com.example.aigc.dto.contract;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/** POST /api/admin/model-configs：新增自定义模型，默认未启用。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ModelCreateRequest {

    @NotBlank
    private String feature;

    @NotBlank
    @Size(min = 2, max = 120)
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:+/-]{1,119}$")
    private String model;

    @NotBlank
    @Size(max = 80)
    private String provider;

    @NotBlank
    private String adapter;

    private String endpoint;
    private String requestTemplate;
}
