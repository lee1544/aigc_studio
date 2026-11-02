package com.example.aigc.repository;

import com.example.aigc.entity.project.ProjectContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectContentRepository extends JpaRepository<ProjectContent, Integer> {
}
