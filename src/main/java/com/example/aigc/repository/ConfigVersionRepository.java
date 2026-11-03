package com.example.aigc.repository;

import com.example.aigc.entity.config.ConfigVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ConfigVersionRepository extends JpaRepository<ConfigVersion, Integer> {

    /**
     * 原子递增版本号；行不存在时以 version=2 初始化（首次变更从 1 递增到 2）。
     * 单条 SQL 完成，避免“读-改-写”在并发下丢失递增。
     */
    @Modifying
    @Query(value = "INSERT INTO config_versions (id, version, updated_at) VALUES (1, 2, :now) "
            + "ON DUPLICATE KEY UPDATE version = version + 1, updated_at = :now",
            nativeQuery = true)
    int bump(@Param("now") LocalDateTime now);
}
