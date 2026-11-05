package com.example.aigc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记只允许全局超级管理员访问的接口。
 * <p>
 * 由 {@code SuperAdminInterceptor} 在进入 Controller 前校验，
 * 与 {@code @RequirePermission} 正交：前者是角色/数据范围，后者是权限码。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SuperAdminOnly {

    /** 操作名称，用于 403 提示，例如“创建组织”。 */
    String value() default "";
}
