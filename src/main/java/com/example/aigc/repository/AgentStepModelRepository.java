package com.example.aigc.repository;

import com.example.aigc.entity.config.AgentStepModel;
import com.example.aigc.entity.config.AgentStepModel.PK;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentStepModelRepository extends JpaRepository<AgentStepModel, PK> {
    List<AgentStepModel> findByAgent(String agent);
    void deleteByAgent(String agent);
}
