package com.example.aigc.dto.contract;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.Size;

/**
 * PATCH /api/admin/users/{id}：未传字段沿用。
 * 密码/邮箱/状态修改撤销旧 token 版本。空字符串不改密码。
 */
@Data
public class UpdateUserContractRequest {

    @Size(max = 80)
    private String name;

    @Email
    @Size(max = 50)
    private String email;

    @Size(max = 32)
    private String phone;

    /** "active" / "disabled" */
    private String status;

    /** 空字符串不改密码；非空须至少 6 位。 */
    @Size(max = 256)
    private String password;
}
