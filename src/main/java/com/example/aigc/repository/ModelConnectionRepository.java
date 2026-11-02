package com.example.aigc.repository;

import com.example.aigc.entity.model.ModelConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelConnectionRepository extends JpaRepository<ModelConnection, String> {
    boolean existsByName(String name);
}
