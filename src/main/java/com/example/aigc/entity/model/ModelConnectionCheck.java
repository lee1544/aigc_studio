package com.example.aigc.entity.model;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 连接检测记录。对应 model_connection_checks 表。 */
@Data
@Entity
@Table(name = "model_connection_checks")
public class ModelConnectionCheck implements Serializable {

    @Id
    @Column(name = "connection_id", length = 64)
    private String connectionId;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private String message;

    @Column(name = "latency_ms", nullable = false)
    private Integer latencyMs;
}
