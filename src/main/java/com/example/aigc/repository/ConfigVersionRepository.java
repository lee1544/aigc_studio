package com.example.aigc.repository;

import com.example.aigc.entity.config.ConfigVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfigVersionRepository extends JpaRepository<ConfigVersion, Integer> {
}
