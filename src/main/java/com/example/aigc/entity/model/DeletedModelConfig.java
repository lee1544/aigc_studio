package com.example.aigc.entity.model;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;

/** 已删除模型配置标记，避免停用预设复活。对应 deleted_model_configs 表。 */
@Data
@Entity
@Table(name = "deleted_model_configs")
@IdClass(DeletedModelConfig.PK.class)
public class DeletedModelConfig implements Serializable {

    @Id
    @Column(nullable = false, length = 64)
    private String feature;

    @Id
    @Column(nullable = false, length = 128)
    private String model;

    @Data
    @Embeddable
    public static class PK implements Serializable {
        private String feature;
        private String model;
    }
}
