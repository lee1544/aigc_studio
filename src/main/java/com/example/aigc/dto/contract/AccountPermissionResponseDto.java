package com.example.aigc.dto.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/** GET /api/admin/users/{id}/permissions 返回 {id, features, version}。 */
@Data
public class AccountPermissionResponseDto {

    private Integer id;

    private Map<String, Boolean> features;

    private String version;
}
