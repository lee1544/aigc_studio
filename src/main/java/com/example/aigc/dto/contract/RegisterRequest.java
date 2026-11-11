package com.example.aigc.dto.contract;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/** POST /api/auth/register：创作者自助注册（用户名 + 姓名 + 密码）。features 即使传入也不生效，默认全 false。 */
@Data
public class RegisterRequest {
    @NotBlank
    @Size(min = 6, max = 50)
    private String username;

    @NotBlank
    @Size(max = 80)
    private String name;

    @NotBlank
    @Size(min = 6, max = 256)
    private String password;
}
