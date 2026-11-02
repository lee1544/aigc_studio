package com.example.aigc.dto.contract;

import lombok.AllArgsConstructor;
import lombok.Data;

/** POST /api/admin/login、/api/auth/login、/api/auth/register 返回 {token, user}。 */
@Data
@AllArgsConstructor
public class LoginResult {
    private String token;
    private ContractUserDto user;
}
