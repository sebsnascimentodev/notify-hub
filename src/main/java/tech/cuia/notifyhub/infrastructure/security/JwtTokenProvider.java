package tech.cuia.notifyhub.infrastructure.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tech.cuia.notifyhub.interfaces.rest.controller.AuthController.TokenResponse;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${notify-hub.security.jwt.secret}")
    private String secret;

    @Value("${notify-hub.security.jwt.expiration:3600s}")
    private Duration expiration;

    private SecretKey signingKey;

    @PostConstruct
    void init() {
        // Requer ao menos 256 bits (32 chars) para HS256 — validado na inicialização
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "notify-hub.security.jwt.secret must be at least 32 characters for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(String clientId) {
        var now = new Date();
        return Jwts.builder()
                .subject(clientId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration.toMillis()))
                .signWith(signingKey)
                .compact();
    }

    public TokenResponse generateTokenResponse(String clientId) {
        return new TokenResponse(generate(clientId), "Bearer", expiration.getSeconds());
    }

    public String extractSubject(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private io.jsonwebtoken.Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
