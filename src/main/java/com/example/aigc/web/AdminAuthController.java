package com.example.aigc.web;

import com.example.aigc.dto.contract.AdminLoginRequest;
import com.example.aigc.dto.contract.ContractUserDto;
import com.example.aigc.dto.contract.LoginResult;
import com.example.aigc.exception.ForbiddenException;
import com.example.aigc.service.AuthService;
import com.example.aigc.utils.UserContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 管理员会话接口：/api/admin/login、/api/admin/me。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public LoginResult login(@Valid @RequestBody AdminLoginRequest request) {
        return authService.adminLogin(request);
    }

    /** 仅管理员可调用；普通用户访问返回 403。 */
    @GetMapping("/me")
    public ContractUserDto me() {
        if (!UserContextUtil.isAdmin()) {
            throw new ForbiddenException("仅管理员可访问该接口");
        }
        return authService.currentUser(UserContextUtil.getCurrentUserId());
    }
}
