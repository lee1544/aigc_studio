package com.example.aigc.entity.ops;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;

/** 平台参数（单行 id=1，不含 features）。对应 system_settings 表。 */
@Data
@Entity
@Table(name = "system_settings")
public class SystemSetting implements Serializable {

    @Id
    private Integer id;

    @Column(nullable = false)
    private String data = "{}";
}
