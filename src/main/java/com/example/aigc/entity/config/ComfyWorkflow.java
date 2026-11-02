package com.example.aigc.entity.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 工作流配置（comfy_workflows）。Java 持久化配置；运行/任务/输出归 Node。 */
@Data
@Entity
@Table(name = "comfy_workflows")
public class ComfyWorkflow implements Serializable {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private String description = "";

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @JsonIgnore
    @Column(name = "api_key", nullable = false)
    private String apiKey = "";

    @Column(nullable = false)
    private String graph;

    @Column(name = "prompt_node", nullable = false)
    private String promptNode;

    @Column(name = "prompt_input", nullable = false)
    private String promptInput = "text";

    @Column(nullable = false)
    private Integer enabled = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(nullable = false, length = 32)
    private String provider = "comfyui";

    @Column(name = "remote_workflow_id", nullable = false)
    private String remoteWorkflowId = "";

    @Column(nullable = false, length = 32)
    private String purpose = "general";

    @Column(name = "media_bindings", nullable = false)
    private String mediaBindings = "{}";
}
