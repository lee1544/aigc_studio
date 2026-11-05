package com.example.aigc.dto.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Map;

/** POST /api/admin/users：创建创作者账号，可选 features。 */
@Data
public class CreateUserContractRequest {

    @NotBlank
    @Size(max = 80)
    private String name;

    @Email
    @NotBlank
    @Size(max = 50)
    private String email;

    @NotBlank
    @Size(min = 6, max = 256)
    private String password;

    /** 省略时保存完整 false 权限；传入时仅接受已知 25 项 feature 布尔。 */
    private Map<String, Boolean> features;

    /** 目标组织；仅超级管理员在按组织筛选时生效，组织管理员传入会被忽略。 */
    private Integer orgId;
}
