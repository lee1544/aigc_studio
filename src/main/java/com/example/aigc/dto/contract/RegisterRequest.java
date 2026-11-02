package com.example.aigc.dto.contract;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** POST /api/auth/register：创作者自助注册。features 即使传入也不生效，默认全 false。 */
@Data
public class RegisterRequest {
    @NotBlank
    @Size(max = 80)
    private String name;

    @Email
    @NotBlank
    private String email;

    @NotBlank
    @Size(min = 6, max = 256)
    private String password;
}
