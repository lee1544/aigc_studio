package com.example.aigc.dto.contract;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** PATCH /api/projects/{id}/module：切换项目当前模块。 */
@Data
public class ProjectModuleRequest {

    @NotBlank(message = "module 不能为空")
    @Size(max = 64, message = "module 长度不能超过 64 个字符")
    private String module;
}
