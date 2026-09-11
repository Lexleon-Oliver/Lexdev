package net.ddns.lexdev.systempro_api.config;

import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey accessKey;
    private final SecretKey refreshKey;
    private final String issuer;
    private final String audience;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret.access}") String accessSecret,
            @Value("${jwt.secret.refresh}") String refreshSecret,
            @Value("${jwt.issuer}") String issuer,
            @Value("${jwt.audience}") String audience,
            @Value("${jwt.expiration}") long accessTokenExpiration,     // 15 min
            @Value("${jwt.refresh-token.expiration}") long refreshTokenExpiration // 7 dias
    ) {
        this.accessKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(accessSecret));
        this.refreshKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(refreshSecret));
        this.issuer = issuer;
        this.audience = audience;
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public record TokenHolder(String token, String jti, long durationMs) {}

    // Access Token: apenas 'sub', 'jti' e 'type'
    public TokenHolder generateAccessToken(String username) {
        String jti = UUID.randomUUID().toString();
        JwtBuilder builder = Jwts.builder()
                .claim("jti", jti)
                .claim("type", "access")
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(accessKey);

        if (issuer != null && !issuer.isBlank()) builder.issuer(issuer);
        if (audience != null && !audience.isBlank()) builder.audience().add(audience);

        return new TokenHolder(builder.compact(), jti, accessTokenExpiration);
    }

    // Refresh Token: apenas 'sub', 'jti' e 'type'
    public TokenHolder generateRefreshToken(String username) {
        String jti = UUID.randomUUID().toString();
        JwtBuilder builder = Jwts.builder()
                .claim("jti", jti)
                .claim("type", "refresh")
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(refreshKey);

        if (issuer != null && !issuer.isBlank()) builder.issuer(issuer);
        if (audience != null && !audience.isBlank()) builder.audience().add(audience);

        return new TokenHolder(builder.compact(), jti, refreshTokenExpiration);
    }

    public boolean validateAccessToken(String token) {
        return validateToken(token, accessKey, "access");
    }

    public boolean validateRefreshToken(String token) {
        return validateToken(token, refreshKey, "refresh");
    }

    public String extractUsernameFromAccessToken(String token) {
        return parseClaims(token, accessKey).getSubject();
    }

    public String extractUsernameFromRefreshToken(String token) {
        return parseClaims(token, refreshKey).getSubject();
    }

    public String extractJtiFromRefreshToken(String token) {
        return parseClaims(token, refreshKey).get("jti", String.class);
    }

    private boolean validateToken(String token, SecretKey key, String expectedType) {
        try {
            Claims claims = parseClaims(token, key);
            return expectedType.equals(claims.get("type", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            logger.debug("Validação falhou para token ({}): {}", expectedType, e.getMessage());
            return false;
        }
    }

    private Claims parseClaims(String token, SecretKey key) {
        JwtParserBuilder builder = Jwts.parser().verifyWith(key);
        if (issuer != null && !issuer.isBlank()) builder.requireIssuer(issuer);
        if (audience != null && !audience.isBlank()) builder.requireAudience(audience);

        return builder.build().parseSignedClaims(token).getPayload();
    }
}
