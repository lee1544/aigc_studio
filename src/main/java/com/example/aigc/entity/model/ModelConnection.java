package com.example.aigc.entity.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;

/** 服务连接（密钥 + 根地址）。对应 model_connections 表。 */
@Data
@Entity
@Table(name = "model_connections")
public class ModelConnection implements Serializable {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(nullable = false, length = 255)
    private String baseUrl;

    @JsonIgnore
    @Column(nullable = false)
    private String apiKey;
}
