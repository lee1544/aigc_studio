package com.example.aigc.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 折叠请求路径中重复的斜杠，例如 {@code //api//users} 归一化为 {@code /api/users}。
 *
 * <p>Spring Security 的 {@code StrictHttpFirewall} 会以“URL 含有潜在恶意字符串 //”为由
 * 拒绝这类请求；网关或客户端把 baseUrl（以 {@code /} 结尾）和接口路径（以 {@code /} 开头）
 * 直接拼接时就会产生重复斜杠。本过滤器在安全过滤链之前归一化，既保留防火墙的其余校验，
 * 又避免正常业务请求被误拒。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DuplicateSlashRequestFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (containsDuplicateSlash(request.getRequestURI())) {
            filterChain.doFilter(new NormalizedRequest(request), response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean containsDuplicateSlash(String path) {
        return path != null && path.contains("//");
    }

    /** 连续斜杠折叠为一个，其余字符（含查询串之外的原始编码）原样保留。 */
    static String collapseDuplicateSlashes(String path) {
        if (path == null || !path.contains("//")) {
            return path;
        }
        StringBuilder normalized = new StringBuilder(path.length());
        char previous = 0;
        for (int i = 0; i < path.length(); i++) {
            char current = path.charAt(i);
            if (current == '/' && previous == '/') {
                continue;
            }
            normalized.append(current);
            previous = current;
        }
        return normalized.toString();
    }

    /** 只覆盖安全过滤链读取的路径方法，其余行为保持原请求不变。 */
    private static final class NormalizedRequest extends HttpServletRequestWrapper {

        private final String requestUri;
        private final String servletPath;
        private final String pathInfo;

        private NormalizedRequest(HttpServletRequest request) {
            super(request);
            this.requestUri = collapseDuplicateSlashes(request.getRequestURI());
            this.servletPath = collapseDuplicateSlashes(request.getServletPath());
            this.pathInfo = collapseDuplicateSlashes(request.getPathInfo());
        }

        @Override
        public String getRequestURI() {
            return requestUri;
        }

        @Override
        public String getServletPath() {
            return servletPath;
        }

        @Override
        public String getPathInfo() {
            return pathInfo;
        }

        @Override
        public StringBuffer getRequestURL() {
            StringBuffer url = new StringBuffer();
            url.append(getScheme()).append("://").append(getServerName());
            int port = getServerPort();
            boolean defaultPort = ("http".equals(getScheme()) && port == 80)
                    || ("https".equals(getScheme()) && port == 443);
            if (port > 0 && !defaultPort) {
                url.append(':').append(port);
            }
            String contextPath = getContextPath();
            url.append(contextPath == null ? "" : contextPath).append(requestUri);
            return url;
        }
    }
}
