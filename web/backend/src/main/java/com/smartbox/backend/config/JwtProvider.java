package com.smartbox.backend.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
@Getter
public class JwtProvider {

    private static final Pattern ALG = Pattern.compile("\"alg\"\\s*:\\s*\"([A-Za-z0-9]+)\"");

    @Value("${smartbox.jwt.secret:}")
    private String jwtSecret;

    @Value("${smartbox.jwt.access-expiration-ms:86400000}")
    private long accessExpirationMs;

    @Value("${smartbox.jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    private SecretKey getSigningKey() {
        if (jwtSecret == null || jwtSecret.isBlank()) throw new IllegalStateException("JWT signing is not configured");
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtSecret);
        } catch (Exception e) {
            keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(UUID userId, String email, String role, String fullName) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessExpirationMs);

        return Jwts.builder()
                .subject(userId != null ? userId.toString() : UUID.randomUUID().toString())
                .claim("email", email)
                .claim("role", role)
                .claim("fullName", fullName)
                .claim("type", "ACCESS")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(UUID userId, String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpirationMs);

        return Jwts.builder()
                .subject(userId != null ? userId.toString() : UUID.randomUUID().toString())
                .claim("email", email)
                .claim("type", "REFRESH")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public boolean validateToken(String token) {
        String alg = headerAlgorithm(token);
        if (alg != null && !alg.startsWith("HS")) {
            // Token Supabase ký bất đối xứng (ES256/RS256): không kiểm tra được bằng JWT_SECRET.
            // Không phải lỗi; JwtAuthenticationFilter sẽ xác thực qua Supabase /auth/v1/user.
            log.debug("Bỏ qua kiểm tra cục bộ cho token alg={}", alg);
            return false;
        }
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.error("Token JWT không hợp lệ: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.warn("Token JWT đã hết hạn: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("Token JWT không được hỗ trợ: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("Chuỗi Claims JWT rỗng: {}", e.getMessage());
        }
        return false;
    }

    /** Đọc "alg" trong header JWT (chưa xác minh chữ ký); null nếu token không đọc được. */
    static String headerAlgorithm(String token) {
        if (token == null) {
            return null;
        }
        int dot = token.indexOf('.');
        if (dot <= 0) {
            return null;
        }
        try {
            String header = new String(Base64.getUrlDecoder().decode(token.substring(0, dot)), StandardCharsets.UTF_8);
            Matcher matcher = ALG.matcher(header);
            return matcher.find() ? matcher.group(1) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID extractUserId(String token) {
        Claims claims = extractClaims(token);
        return UUID.fromString(claims.getSubject());
    }

    public String extractEmail(String token) {
        Claims claims = extractClaims(token);
        return claims.get("email", String.class);
    }

    public String extractRole(String token) {
        Claims claims = extractClaims(token);
        return claims.get("role", String.class);
    }
}
