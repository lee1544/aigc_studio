package com.example.aigc.entity.config;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;

/** Agent 步骤模型绑定（原始选择）。对应 agent_step_models 表，PK(agent, step)。 */
@Data
@Entity
@Table(name = "agent_step_models")
@IdClass(AgentStepModel.PK.class)
public class AgentStepModel implements Serializable {

    @Id
    @Column(nullable = false, length = 32)
    private String agent;

    @Id
    @Column(nullable = false, length = 32)
    private String step;

    @Column(nullable = false, length = 128)
    private String model;

    @Data
    @Embeddable
    public static class PK implements Serializable {
        private String agent;
        private String step;
    }
}
