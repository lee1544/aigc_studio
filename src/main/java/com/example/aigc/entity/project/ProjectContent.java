package com.example.aigc.entity.project;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 项目正文。对应 project_contents 表，PK=project_id。 */
@Data
@Entity
@Table(name = "project_contents")
public class ProjectContent implements Serializable {

    @Id
    @Column(name = "project_id")
    private Integer projectId;

    @Column(nullable = false)
    private String script = "";

    @Column(nullable = false)
    private String scenes = "[]";

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(nullable = false)
    private String media = "{}";
}
