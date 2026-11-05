package com.example.aigc.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class CreateUserRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 6, max = 50, message = "用户名长度6-50个字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度6-100个字符")
    private String password;

    @NotBlank(message = "姓名不能为空")
    private String realName;

    /**
     * 目标组织。仅超级管理员在管理后台按组织筛选后创建用户时使用；
     * 组织管理员传入会被忽略，始终落到当前组织。
     */
    private Integer orgId;
}
