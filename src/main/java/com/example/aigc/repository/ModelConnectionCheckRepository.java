package com.example.aigc.repository;

import com.example.aigc.entity.model.ModelConnectionCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelConnectionCheckRepository extends JpaRepository<ModelConnectionCheck, String> {
}
