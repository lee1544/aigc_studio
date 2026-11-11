package com.example.aigc.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 创建组织请求。
 * <p>组织创作账号信息必填：创建组织时同步创建该账号并绑定组织创作角色。
 * 该账号是学校创作账号（role=user），用用户名 + 密码登录创作平台，不是管理后台管理员。
 */
@Data
public class CreateOrganizationRequest {

    @NotBlank(message = "组织编码不能为空")
    @Size(max = 50, message = "组织编码长度不能超过50个字符")
    private String orgCode;

    @NotBlank(message = "组织名称不能为空")
    @Size(max = 100, message = "组织名称长度不能超过100个字符")
    private String orgName;

    @Size(max = 255, message = "描述长度不能超过255个字符")
    private String description;

    @NotBlank(message = "管理员用户名不能为空")
    @Size(min = 6, max = 50, message = "管理员用户名长度6-50个字符")
    private String adminUsername;

    @NotBlank(message = "管理员密码不能为空")
    @Size(min = 6, max = 100, message = "管理员密码长度6-100个字符")
    private String adminPassword;

    @NotBlank(message = "管理员姓名不能为空")
    @Size(max = 50, message = "管理员姓名长度不能超过50个字符")
    private String adminRealName;
}
