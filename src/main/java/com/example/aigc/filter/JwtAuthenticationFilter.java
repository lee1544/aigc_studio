package com.example.aigc.filter;

import com.example.aigc.utils.JwtTokenUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private JwtTokenBlacklist jwtTokenBlacklist;

    @Autowired
    private UserSessionCache userSessionCache;

    @Value("${dev.bypass-token}")
    private String bypassToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = getTokenFromRequest(request);

        if (token != null) {
            if (StringUtils.hasText(bypassToken) && bypassToken.equals(token)) {
                request.setAttribute("userId", 1);
                request.setAttribute("orgId", 1);
                request.setAttribute("username", "developer");
                request.setAttribute("role", "admin");
                request.setAttribute("isDevBypass", true);
                // 开发 bypass 等价于平台管理员，数据范围不限制组织
                request.setAttribute("isSuperAdmin", true);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken("developer", null, new ArrayList<>());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                Claims claims;
                try {
                    // 一次解析完成签名 + 过期校验，后续字段直接复用
                    claims = jwtTokenUtil.parseClaims(token);
                } catch (Exception e) {
                    // 无效或已过期：按未登录继续，由 Security 统一返回 401
                    filterChain.doFilter(request, response);
                    return;
                }
                // 检查 token 是否已被注销
                if (jwtTokenBlacklist.isInvalid(token)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                Object userIdClaim = claims.get("userId");
                if (!(userIdClaim instanceof Number)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                Integer userId = ((Number) userIdClaim).intValue();
                Object orgIdClaim = claims.get("orgId");
                Integer orgId = orgIdClaim instanceof Number ? ((Number) orgIdClaim).intValue() : null;
                Object versionClaim = claims.get("tokenVersion");
                Integer tokenVersion = versionClaim instanceof Number ? ((Number) versionClaim).intValue() : 0;
                Object roleClaim = claims.get("role");
                String role = roleClaim == null ? "user" : roleClaim.toString();
                String username = claims.getSubject();

                // 会话版本校验：与库中 tokenVersion 不一致则视为已撤销（改密/禁用/删除/退出）
                UserSessionCache.SessionInfo info = userSessionCache.get(userId);
                if (info == null || !java.util.Objects.equals(info.tokenVersion, tokenVersion)) {
                    // 复核一次，避免缓存陈旧
                    info = userSessionCache.loadFresh(userId);
                    if (info == null || !java.util.Objects.equals(info.tokenVersion, tokenVersion)) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                }

                // 组织归属与账号类型以数据库为准（会话缓存按 tokenVersion 失效），
                // 避免旧 token 在账号被降级/调组织后仍按旧 claim 放行
                Integer effectiveOrgId = info.orgId != null ? info.orgId : orgId;
                String effectiveRole = info.role != null ? info.role : role;
                request.setAttribute("userId", userId);
                request.setAttribute("orgId", effectiveOrgId);
                request.setAttribute("username", username);
                request.setAttribute("role", effectiveRole);
                request.setAttribute("isSuperAdmin", info.superAdmin);
                request.setAttribute("userPermissions", info.permissions);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, new ArrayList<>());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
