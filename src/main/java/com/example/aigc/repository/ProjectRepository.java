package com.example.aigc.repository;

import com.example.aigc.entity.project.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Integer> {
    List<Project> findByUserIdOrderByUpdatedAtDesc(Integer userId);

    Page<Project> findByUserIdOrderByUpdatedAtDesc(Integer userId, Pageable pageable);
}
