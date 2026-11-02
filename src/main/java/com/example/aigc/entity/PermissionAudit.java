package com.example.aigc.entity;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 账号功能权限变更审计。对应前端契约 permission_audits 表。
 */
@Data
@Entity
@Table(name = "permission_audits")
public class PermissionAudit implements Serializable {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "operator_id", nullable = false)
    private Integer operatorId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    /** replace / grant / revoke */
    @Column(nullable = false, length = 16)
    private String mode;

    @Column(name = "before_json", nullable = false)
    private String beforeJson;

    @Column(name = "after_json", nullable = false)
    private String afterJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
