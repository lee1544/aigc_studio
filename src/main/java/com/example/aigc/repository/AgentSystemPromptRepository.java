package com.example.aigc.repository;

import com.example.aigc.entity.config.AgentSystemPrompt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentSystemPromptRepository extends JpaRepository<AgentSystemPrompt, String> {
}
