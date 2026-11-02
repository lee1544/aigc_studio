package com.example.aigc.entity.config;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 配置版本（单行 id=1）：任何非计费配置变更 +1，供内部 node-config 快照使用。 */
@Data
@Entity
@Table(name = "config_versions")
public class ConfigVersion implements Serializable {

    @Id
    private Integer id;

    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
