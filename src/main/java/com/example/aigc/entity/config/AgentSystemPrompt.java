package com.example.aigc.entity.config;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;

/** Agent 系统提示词。对应 agent_system_prompts 表，PK=agent。 */
@Data
@Entity
@Table(name = "agent_system_prompts")
public class AgentSystemPrompt implements Serializable {

    @Id
    @Column(nullable = false, length = 32)
    private String agent;

    @Column(nullable = false)
    private String systemPrompt = "";
}
