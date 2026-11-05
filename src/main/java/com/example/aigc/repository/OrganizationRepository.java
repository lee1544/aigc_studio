package com.example.aigc.repository;

import com.example.aigc.entity.Organization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Integer> {
    
    Optional<Organization> findByOrgCode(String orgCode);
    
    boolean existsByOrgCode(String orgCode);
    
    boolean existsByOrgName(String orgName);

    /**
     * 组织分页搜索。
     *
     * @param keyword 组织编码/名称模糊匹配，可为 null
     * @param orgId   数据范围：非 null 时只返回该组织（组织管理员），null 表示全部（超级管理员）
     */
    @Query("SELECT o FROM Organization o WHERE " +
            "(:orgId IS NULL OR o.id = :orgId) AND " +
            "(:keyword IS NULL OR o.orgCode LIKE CONCAT('%', :keyword, '%') " +
            "OR o.orgName LIKE CONCAT('%', :keyword, '%'))")
    Page<Organization> search(@Param("keyword") String keyword,
                              @Param("orgId") Integer orgId,
                              Pageable pageable);
}
