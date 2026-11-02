package com.example.aigc.dto.contract;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** POST /api/admin/login：管理员用户名 + 密码。 */
@Data
public class AdminLoginRequest {
    @NotBlank
    private String username;

    @NotBlank
    private String password;
}
