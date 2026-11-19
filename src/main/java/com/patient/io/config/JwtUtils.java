package com.patient.io.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtils {

    @Value("${jwt.secret:patient-io-record-jwt-secret-key-2026-very-secure}")
    private String secret;

    @Value("${jwt.expiration:604800}")
    private Long expiration;

    public String generateToken(Long userId, String openid, String role) {
        return generateToken(userId, openid, role, null);
    }

    public String generateToken(Long userId, String openid, String role, Long hospitalId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("openid", openid);
        claims.put("role", role);
        if (hospitalId != null) {
            claims.put("hospitalId", hospitalId);
        }
        return generateToken(claims);
    }

    public String generateToken(Map<String, Object> claims) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration * 1000);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }

    public Claims getClaimsFromToken(String token) {
        try {
            return Jwts.parser()
                    .setSigningKey(secret)
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            return null;
        }
    }

    public boolean validateToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null && !claims.getExpiration().before(new Date());
    }
}
