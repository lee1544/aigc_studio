package com.example.aigc.repository;

import com.example.aigc.entity.PermissionAudit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PermissionAuditRepository extends JpaRepository<PermissionAudit, String> {

    /** 最近 100 条，按时间倒序；可选按 userId 过滤。 */
    List<PermissionAudit> findByUserIdOrderByCreatedAtDesc(Integer userId, Pageable pageable);

    List<PermissionAudit> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
