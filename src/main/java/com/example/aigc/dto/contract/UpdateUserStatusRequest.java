package com.example.aigc.dto.contract;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * PATCH /api/admin/users/{id}/status：body 只需 {status}。
 * id 为可选（兼容旧客户端）；若传入且与路径 id 不一致由控制器返回 400。
 */
@Data
public class UpdateUserStatusRequest {

    private Integer id;

    /** "active" / "disabled" */
    @NotNull
    private String status;
}
