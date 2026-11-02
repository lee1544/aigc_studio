package com.example.aigc.dto.contract;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

/** POST /api/auth/login：创作者 email + 密码。 */
@Data
public class CreatorLoginRequest {
    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String password;
}
