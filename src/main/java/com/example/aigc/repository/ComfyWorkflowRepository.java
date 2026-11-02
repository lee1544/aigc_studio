package com.example.aigc.repository;

import com.example.aigc.entity.config.ComfyWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComfyWorkflowRepository extends JpaRepository<ComfyWorkflow, String> {
    List<ComfyWorkflow> findAllByOrderByCreatedAtDesc();
}
