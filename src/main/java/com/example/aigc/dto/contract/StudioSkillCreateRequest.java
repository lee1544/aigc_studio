package com.example.aigc.dto.contract;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** POST /api/admin/studio-skills：创建 Skill 模板。 */
@Data
public class StudioSkillCreateRequest {

    @NotBlank(message = "name 不能为空")
    @Size(max = 100, message = "name 长度不能超过 100 个字符")
    private String name;

    /** 省略时保存为空串。 */
    private String description;

    /** 省略时保存为空串。 */
    private String content;

    /** 省略时默认启用。 */
    private Boolean enabled;
}
