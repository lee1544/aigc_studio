package com.example.aigc.repository;

import com.example.aigc.entity.model.DeletedModelConfig;
import com.example.aigc.entity.model.DeletedModelConfig.PK;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeletedModelConfigRepository extends JpaRepository<DeletedModelConfig, PK> {
    boolean existsByFeatureAndModel(String feature, String model);
}
