package com.example.aigc.entity;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "t_role")
public class Role implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(unique = true, nullable = false, length = 50)
    private String roleCode;
    
    @Column(nullable = false, length = 50)
    private String roleName;
    
    private String description;
    
    private Integer orgId;

    /** 角色权限版本：每次整体覆盖权限 +1，供 expectedVersion 乐观锁使用。 */
    @Column(nullable = false)
    private Integer permissionVersion = 0;
    
    private Integer status;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
    
    @Transient
    private List<Permission> permissions;

    /** 所属组织名称（列表展示用，org_id 为 NULL 的全局角色为“全局”）。 */
    @Transient
    private String orgName;
}
