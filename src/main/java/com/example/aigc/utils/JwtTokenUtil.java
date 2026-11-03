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

    /** HS512 鐨勬渶灏忓彲鐢ㄩ暱搴︼紱浣庝簬璇ュ€煎惎鍔ㄥ嵆澶辫触锛岄伩鍏嶈繍琛屾湡鎵嶆毚闇茬鍚嶅紓甯搞€?*/
    private static final int MIN_SECRET_LENGTH = 32;

    @Value("${jwt.secret}")
    private String secret;
    
    @Value("${jwt.expiration:86400}")
    private Long expiration;

    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException(
                    "JWT_SECRET 鏈厤缃細璇烽€氳繃鐜鍙橀噺娉ㄥ叆鑷冲皯 " + MIN_SECRET_LENGTH + " 涓瓧绗︾殑瀵嗛挜");
        }
        if (secret.trim().length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT_SECRET 杩囩煭锛氳嚦灏?" + MIN_SECRET_LENGTH + " 涓瓧绗︼紝褰撳墠 " + secret.trim().length());
        }
        if (secret.trim().length() < 64) {
            logger.warn("JWT_SECRET 闀垮害涓?{}锛孒S512 寤鸿 64 瀛楄妭浠ヤ笂瀵嗛挜", secret.trim().length());
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
     * 濂戠害绛惧彂锛氭惡甯?tokenVersion锛堜細璇濈増鏈級涓?role锛坅dmin/user锛夈€?
     * exp 浠嶄互姣瀛樺偍锛坖jwt Date锛夛紝绗﹀悎鍓嶇濂戠害銆?
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
        return parseClaims(token).getSubject();
    }
    
    public Integer getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return (Integer) claims.get("userId");
    }
    
    public Integer getOrgIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return (Integer) claims.get("orgId");
    }

    public Integer getTokenVersionFromToken(String token) {
        Claims claims = parseClaims(token);
        Object v = claims.get("tokenVersion");
        if (v instanceof Integer) {
            return (Integer) v;
        }
        return 0;
    }

    public String getRoleFromToken(String token) {
        Claims claims = parseClaims(token);
        Object v = claims.get("role");
        return v == null ? "user" : v.toString();
    }
    
    public Boolean validateToken(String token) {
        try {
            // 瑙ｆ瀽鎴愬姛鍗充唬琛ㄧ鍚嶄笌 exp 鍧囨湁鏁堬紙jjwt 浼氬杩囨湡 token 鎶?ExpiredJwtException锛?
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 瑙ｆ瀽骞舵牎楠屼竴娆★紝渚涜姹傝繃婊ゅ櫒澶嶇敤锛岄伩鍏嶅悓涓€ token 鍙嶅鍋氱鍚嶆牎楠屻€?*/
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();
    }
}
