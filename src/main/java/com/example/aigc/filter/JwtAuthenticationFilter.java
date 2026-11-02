package com.example.aigc.filter;

import com.example.aigc.utils.JwtTokenUtil;
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

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken("developer", null, new ArrayList<>());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else if (jwtTokenUtil.validateToken(token)) {
                // 检查 token 是否已被注销
                if (jwtTokenBlacklist.isInvalid(token)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                String username = jwtTokenUtil.getUsernameFromToken(token);
                Integer userId = jwtTokenUtil.getUserIdFromToken(token);
                Integer orgId = jwtTokenUtil.getOrgIdFromToken(token);
                Integer tokenVersion = jwtTokenUtil.getTokenVersionFromToken(token);
                String role = jwtTokenUtil.getRoleFromToken(token);

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

                request.setAttribute("userId", userId);
                request.setAttribute("orgId", orgId);
                request.setAttribute("username", username);
                request.setAttribute("role", role);
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
