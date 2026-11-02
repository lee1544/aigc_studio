package com.example.aigc.repository;

import com.example.aigc.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Integer> {
    
    List<RolePermission> findByRoleId(Integer roleId);

    List<RolePermission> findByRoleIdIn(List<Integer> roleIds);

    /**
     * 批量删除某角色的全部授权。
     * <p>
     * 必须用 JPQL 批量 DELETE 而不是派生删除（select + 逐行 remove）：后者把删除排入 Hibernate
     * 动作队列，而同一事务内刷新顺序是 insert 先于 delete，于是"先删后重新插入同一组
     * (role_id, permission_id)"会撞 uk_role_permission 唯一索引。
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from RolePermission rp where rp.roleId = :roleId")
    void deleteByRoleId(@Param("roleId") Integer roleId);
}
