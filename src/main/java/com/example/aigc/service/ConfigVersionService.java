package com.example.aigc.service;

import com.example.aigc.entity.config.ConfigVersion;
import com.example.aigc.repository.ConfigVersionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 配置版本管理：任何非计费配置（连接/模型/Agent/工作流/Skill/系统设置）变更时 bump。
 * 供内部 node-config 快照的不透明 version 字段使用。
 */
@Service
public class ConfigVersionService {

    @Autowired
    private ConfigVersionRepository repository;

    public ConfigVersion current() {
        return repository.findById(1).orElseGet(() -> {
            ConfigVersion v = new ConfigVersion();
            v.setId(1);
            v.setVersion(1);
            v.setUpdatedAt(LocalDateTime.now());
            return repository.save(v);
        });
    }

    @Transactional
    public void bump() {
        repository.bump(LocalDateTime.now());
    }
}
