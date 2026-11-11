package com.example.aigc.web;

import com.example.aigc.dto.contract.ContractUserDto;
import com.example.aigc.dto.contract.CreatorLoginRequest;
import com.example.aigc.dto.contract.LoginResult;
import com.example.aigc.dto.contract.PermissionResponseDto;
import com.example.aigc.dto.contract.RegisterRequest;
import com.example.aigc.service.AuthService;
import com.example.aigc.service.FeaturePermissionService;
import com.example.aigc.utils.UserContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * 创作者会话接口：/api/auth/*。
 * login(username)、register、me、permissions、logout。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private FeaturePermissionService featurePermissionService;

    @PostMapping("/login")
    public LoginResult login(@Valid @RequestBody CreatorLoginRequest request) {
        return authService.creatorLogin(request);
    }

    @PostMapping("/register")
    public LoginResult register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @GetMapping("/me")
    public ContractUserDto me() {
        return authService.currentUser(UserContextUtil.getCurrentUserId());
    }

    /** 旧管理后台兼容：page.js 仍调用 /api/auth/userinfo，与 /me 返回同一份会话用户。 */
    @GetMapping("/userinfo")
    public ContractUserDto userinfo() {
        return authService.currentUser(UserContextUtil.getCurrentUserId());
    }

    @GetMapping("/permissions")
    public PermissionResponseDto permissions() {
        PermissionResponseDto dto = new PermissionResponseDto();
        dto.setFeatures(featurePermissionService.getFeatures(UserContextUtil.getCurrentUserId()));
        return dto;
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            authService.logout(token.substring(7));
        }
        Map<String, Object> ok = new HashMap<>();
        ok.put("ok", true);
        return ok;
    }
}
