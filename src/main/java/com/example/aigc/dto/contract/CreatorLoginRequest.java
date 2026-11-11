package com.example.aigc.dto.contract;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/** POST /api/auth/login：创作者用户名 + 密码。 */
@Data
public class CreatorLoginRequest {
    @NotBlank
    private String username;

    @NotBlank
    private String password;
}
