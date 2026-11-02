package com.example.aigc.dto.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/** PUT /api/admin/user-permissions 返回 {ok, updated, versions}。 */
@Data
public class PermissionResultDto {

    private boolean ok;

    private int updated;

    @JsonProperty("versions")
    private Map<Integer, String> versions;
}
