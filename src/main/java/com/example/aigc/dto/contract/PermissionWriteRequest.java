package com.example.aigc.dto.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/** PUT /api/admin/user-permissions：批量授权。 */
@Data
public class PermissionWriteRequest {

    @NotEmpty
    @Size(max = 500)
    private List<Integer> userIds;

    /** replace / grant / revoke */
    @NotNull
    private String mode;

    private Map<String, Boolean> features;

    /** replace 必传；批量预览提交时也应传入。 */
    @JsonProperty("expectedVersions")
    private Map<Integer, String> expectedVersions;
}
