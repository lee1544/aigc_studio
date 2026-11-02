package com.example.aigc.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtTokenUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenUtil.class);

    /** HS512 的最小可用长度；低于该值启动即失败，避免运行期才暴露签名异常。 */
    private static final int MIN_SECRET_LENGTH = 32;

    @Value("${jwt.secret}")
    private String secret;
    
    @Value("${jwt.expiration:86400}")
    private Long expiration;

    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException(
                    "JWT_SECRET 未配置：请通过环境变量注入至少 " + MIN_SECRET_LENGTH + " 个字符的密钥");
        }
        if (secret.trim().length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT_SECRET 过短：至少 " + MIN_SECRET_LENGTH + " 个字符，当前 " + secret.trim().length());
        }
        if (secret.trim().length() < 64) {
            logger.warn("JWT_SECRET 长度为 {}，HS512 建议 64 字节以上密钥", secret.trim().length());
        }
    }
    
    public String generateToken(String username, Integer userId, Integer orgId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("orgId", orgId);
        claims.put("tokenVersion", 0);
        claims.put("role", "user");
        return createToken(claims, username);
    }

    /**
     * 契约签发：携带 tokenVersion（会话版本）与 role（admin/user）。
     * exp 仍以毫秒存储（jjwt Date），符合前端契约。
     */
    public String generateToken(String subject, Integer userId, Integer orgId,
                                Integer tokenVersion, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("orgId", orgId);
        claims.put("tokenVersion", tokenVersion == null ? 0 : tokenVersion);
        claims.put("role", role == null ? "user" : role);
        return createToken(claims, subject);
    }
    
    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expirationDate = new Date(now.getTime() + expiration * 1000);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expirationDate)
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }
    
    public String getUsernameFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }
    
    public Integer getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return (Integer) claims.get("userId");
    }
    
    public Integer getOrgIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return (Integer) claims.get("orgId");
    }

    public Integer getTokenVersionFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        Object v = claims.get("tokenVersion");
        if (v instanceof Integer) {
            return (Integer) v;
        }
        return 0;
    }

    public String getRoleFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        Object v = claims.get("role");
        return v == null ? "user" : v.toString();
    }
    
    public Boolean validateToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return !isTokenExpired(claims);
        } catch (Exception e) {
            return false;
        }
    }
    
    private Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();
    }
    
    private Boolean isTokenExpired(Claims claims) {
        Date expiration = claims.getExpiration();
        return expiration.before(new Date());
    }
}
