package com.example.aigc.repository;

import com.example.aigc.entity.ops.StudioSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudioSkillRepository extends JpaRepository<StudioSkill, String> {
    List<StudioSkill> findByEnabled(Integer enabled);
}
