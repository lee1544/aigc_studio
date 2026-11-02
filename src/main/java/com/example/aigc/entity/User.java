package com.example.aigc.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "t_user")
public class User implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Column(length = 50)
    private String realName;

    /** 前端契约 User.name：展示名（管理员或创作者）。 */
    @Column(length = 80)
    private String name;

    @Column(unique = true, length = 254)
    private String email;

    @Column(length = 32)
    private String phone;

    /** "admin" 或 "user"，对应契约 role 枚举。 */
    @Column(nullable = false, length = 16)
    private String role = "user";

    /** 会话版本：改密/禁用/删除/改邮箱/改状态时 +1，使旧 token 立即失效。 */
    @Column(nullable = false)
    private Integer tokenVersion = 0;

    /** 权限版本：仅功能权限变更时 +1，不撤销会话；用于 expectedVersions 乐观锁。 */
    @Column(nullable = false)
    private Integer permissionVersion = 0;

    @Column(length = 32)
    private String accountSource = "历史账号";

    @Column(nullable = false)
    private Integer orgId;

    /** 1=active, 0=disabled, -1=deleted（软删除）。 */
    private Integer status;
    
    private LocalDateTime lastLoginTime;
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
    
    @Transient
    private String orgName;

    @Transient
    private List<Integer> roleIds;

    @Transient
    private List<String> roleNames;
}
