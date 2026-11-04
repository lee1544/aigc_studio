package com.example.aigc.entity.project;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 普通项目（归属用户）。对应 projects 表。 */
@Data
@Entity
@Table(name = "projects")
public class Project implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id")
    @JsonIgnore
    private Integer userId;

    @Column(length = 100)
    private String title;

    @Column(length = 32)
    private String type;

    @Column(length = 32)
    private String style;

    private Integer duration;

    @Column(length = 16)
    private String status = "draft";

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(nullable = false, length = 64)
    private String module = "script";
}
