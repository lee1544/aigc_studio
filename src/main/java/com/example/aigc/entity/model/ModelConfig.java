package com.example.aigc.entity.model;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;

/** 模型功能绑定配置。对应 model_configs 表，复合主键 (feature, model)。 */
@Data
@Entity
@Table(name = "model_configs")
@IdClass(ModelConfig.PK.class)
public class ModelConfig implements Serializable {

    @Id
    @Column(nullable = false, length = 64)
    private String feature;

    @Id
    @Column(nullable = false, length = 128)
    private String model;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(nullable = false, length = 64)
    private String adapter;

    @Column(nullable = false, length = 255)
    private String endpoint;

    @Column(nullable = false)
    private String apiKey = "";

    @Column(nullable = false)
    private Integer enabled = 0;

    @Column(nullable = false)
    private Integer custom = 0;

    @Column(nullable = false)
    private String requestTemplate = "";

    @Column(name = "connection_id")
    private String connectionId;

    @Data
    @Embeddable
    public static class PK implements Serializable {
        private String feature;
        private String model;
    }
}
