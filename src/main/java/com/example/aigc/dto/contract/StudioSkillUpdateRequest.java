package com.example.aigc.dto.contract;

import lombok.Data;

import javax.validation.constraints.Size;

/** PUT /api/admin/studio-skills/{id}：部分更新，字段为 null 表示不修改。 */
@Data
public class StudioSkillUpdateRequest {

    @Size(max = 100, message = "name 长度不能超过 100 个字符")
    private String name;

    private String description;

    private String content;

    private Boolean enabled;
}
