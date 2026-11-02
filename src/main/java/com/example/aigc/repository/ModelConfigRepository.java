package com.example.aigc.repository;

import com.example.aigc.entity.model.ModelConfig;
import com.example.aigc.entity.model.ModelConfig.PK;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModelConfigRepository extends JpaRepository<ModelConfig, PK> {

    List<ModelConfig> findByFeature(String feature);

    List<ModelConfig> findByEnabled(Integer enabled);

    List<ModelConfig> findByConnectionId(String connectionId);
}
