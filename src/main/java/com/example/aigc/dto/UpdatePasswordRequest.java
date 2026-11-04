package com.example.aigc.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** PUT /api/users/{id}/password：修改密码必须提供原密码用于校验。 */
@Data
public class UpdatePasswordRequest {

    /** 原密码，用于确认操作人确实掌握该账号当前凭据。 */
    @NotBlank(message = "原密码不能为空")
    @Size(max = 256, message = "原密码长度不能超过256个字符")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 50, message = "新密码长度必须在6到50位之间")
    private String password;
}
