package com.example.aigc.entity.ops;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 平台 Skill 模板。对应 studio_skills 表。 */
@Data
@Entity
@Table(name = "studio_skills")
public class StudioSkill implements Serializable {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private String description = "";

    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private Integer enabled = 1;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
