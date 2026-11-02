package com.example.aigc.dto.contract;

import lombok.Data;

import java.util.Map;

/** GET /api/auth/permissions 返回 {features}。 */
@Data
public class PermissionResponseDto {
    private Map<String, Boolean> features;
}
