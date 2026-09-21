package net.ddns.lexdev.systempro_api.config;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtTokenProviderTest {

    private static final String ISSUER = "lexdev.ddns.net";
    private static final String AUDIENCE = "systempro";

    private static final long ACCESS_EXPIRATION = 900_000L;
    private static final long REFRESH_EXPIRATION = 604_800_000L;

    private static final String ACCESS_SECRET =
            Base64.getEncoder().encodeToString(
                    "access-secret-key-for-tests-0123456789-ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                            .getBytes(StandardCharsets.UTF_8)
            );

    private static final String REFRESH_SECRET =
            Base64.getEncoder().encodeToString(
                    "refresh-secret-key-for-tests-0123456789-ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                            .getBytes(StandardCharsets.UTF_8)
            );

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(
                ACCESS_SECRET,
                REFRESH_SECRET,
                ISSUER,
                AUDIENCE,
                ACCESS_EXPIRATION,
                REFRESH_EXPIRATION
        );
    }

    @Test
    @DisplayName("Deve gerar access token válido")
    void deveGerarAccessTokenValido() {
        Instant before = Instant.now();

        JwtTokenProvider.TokenHolder holder =
                provider.generateAccessToken("admin");

        Instant after = Instant.now();

        assertThat(holder.token()).isNotBlank();
        assertThat(holder.jti()).isNotBlank();
        assertThat(holder.expiresAt())
                .isAfterOrEqualTo(before.plusMillis(ACCESS_EXPIRATION))
                .isBeforeOrEqualTo(after.plusMillis(ACCESS_EXPIRATION));

        assertThat(provider.validateAccessToken(holder.token()))
                .isTrue();

        assertThat(provider.validateRefreshToken(holder.token()))
                .isFalse();
    }

    @Test
    @DisplayName("Deve gerar refresh token válido")
    void deveGerarRefreshTokenValido() {
        JwtTokenProvider.TokenHolder holder =
                provider.generateRefreshToken("admin");

        assertThat(holder.token()).isNotBlank();
        assertThat(holder.jti()).isNotBlank();

        assertThat(provider.validateRefreshToken(holder.token()))
                .isTrue();

        assertThat(provider.validateAccessToken(holder.token()))
                .isFalse();
    }

    @Test
    @DisplayName("Deve gerar JTI único para cada token")
    void deveGerarJtiUnico() {
        JwtTokenProvider.TokenHolder first =
                provider.generateAccessToken("admin");

        JwtTokenProvider.TokenHolder second =
                provider.generateAccessToken("admin");

        assertThat(first.jti()).isNotEqualTo(second.jti());
    }

    @Test
    @DisplayName("Deve extrair username do access token")
    void deveExtrairUsernameDoAccessToken() {
        JwtTokenProvider.TokenHolder holder =
                provider.generateAccessToken("admin");

        assertThat(
                provider.extractUsernameFromAccessToken(holder.token())
        ).isEqualTo("admin");
    }

    @Test
    @DisplayName("Deve extrair username do refresh token")
    void deveExtrairUsernameDoRefreshToken() {
        JwtTokenProvider.TokenHolder holder =
                provider.generateRefreshToken("admin");

        assertThat(
                provider.extractUsernameFromRefreshToken(holder.token())
        ).isEqualTo("admin");
    }

    @Test
    @DisplayName("Deve extrair JTI do refresh token")
    void deveExtrairJtiDoRefreshToken() {
        JwtTokenProvider.TokenHolder holder =
                provider.generateRefreshToken("admin");

        assertThat(
                provider.extractJtiFromRefreshToken(holder.token())
        ).isEqualTo(holder.jti());
    }

    @Test
    @DisplayName("Deve rejeitar access token como refresh token")
    void deveRejeitarAccessTokenComoRefreshToken() {
        String token = provider.generateAccessToken("admin").token();

        assertThat(provider.validateRefreshToken(token)).isFalse();
    }

    @Test
    @DisplayName("Deve rejeitar refresh token como access token")
    void deveRejeitarRefreshTokenComoAccessToken() {
        String token = provider.generateRefreshToken("admin").token();

        assertThat(provider.validateAccessToken(token)).isFalse();
    }

    @Test
    @DisplayName("Deve rejeitar token nulo")
    void deveRejeitarTokenNulo() {
        assertThat(provider.validateAccessToken(null)).isFalse();
        assertThat(provider.validateRefreshToken(null)).isFalse();
    }

    @Test
    @DisplayName("Deve rejeitar token vazio")
    void deveRejeitarTokenVazio() {
        assertThat(provider.validateAccessToken("")).isFalse();
        assertThat(provider.validateRefreshToken("")).isFalse();
    }

    @Test
    @DisplayName("Deve rejeitar token malformado")
    void deveRejeitarTokenMalformado() {
        assertThat(provider.validateAccessToken("token-invalido"))
                .isFalse();

        assertThat(provider.validateRefreshToken("token-invalido"))
                .isFalse();
    }

    @Test
    @DisplayName("Deve rejeitar token com assinatura adulterada")
    void deveRejeitarTokenComAssinaturaAdulterada() {
        String token = provider.generateAccessToken("admin").token();

        String[] parts = token.split("\\.");

        // Altera o payload sem gerar uma nova assinatura.
        parts[1] = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("{\"sub\":\"hacker\"}".getBytes(StandardCharsets.UTF_8));

        String tamperedToken =
                parts[0] + "." + parts[1] + "." + parts[2];

        assertThat(provider.validateAccessToken(tamperedToken))
                .isFalse();
    }

    @Test
    @DisplayName("Deve rejeitar token expirado")
    void deveRejeitarTokenExpirado() {
        SecretKey accessKey =
                Keys.hmacShaKeyFor(Base64.getDecoder().decode(ACCESS_SECRET));

        Instant now = Instant.now();

        String expiredToken = Jwts.builder()
                .subject("admin")
                .claim("jti", UUID.randomUUID().toString())
                .claim("type", "access")
                .issuer(ISSUER)
                .audience()
                    .add(AUDIENCE)
                    .and()
                .issuedAt(Date.from(now.minusSeconds(120)))
                .expiration(Date.from(now.minusSeconds(60)))
                .signWith(accessKey)
                .compact();

        assertThat(provider.validateAccessToken(expiredToken))
                .isFalse();
    }

    @Test
    @DisplayName("Deve rejeitar token assinado com chave diferente")
    void deveRejeitarTokenAssinadoComChaveDiferente() {
        String token = provider.generateAccessToken("admin").token();

        String anotherSecret =
                Base64.getEncoder().encodeToString(
                        "another-access-secret-key-0123456789-ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                                .getBytes(StandardCharsets.UTF_8)
                );

        JwtTokenProvider anotherProvider =
                new JwtTokenProvider(
                        anotherSecret,
                        REFRESH_SECRET,
                        ISSUER,
                        AUDIENCE,
                        ACCESS_EXPIRATION,
                        REFRESH_EXPIRATION
                );

        assertThat(anotherProvider.validateAccessToken(token))
                .isFalse();
    }

    @Test
    @DisplayName("Deve rejeitar token com issuer diferente")
    void deveRejeitarTokenComIssuerDiferente() {
        String token = provider.generateAccessToken("admin").token();

        JwtTokenProvider anotherProvider =
                new JwtTokenProvider(
                        ACCESS_SECRET,
                        REFRESH_SECRET,
                        "outro-issuer",
                        AUDIENCE,
                        ACCESS_EXPIRATION,
                        REFRESH_EXPIRATION
                );

        assertThat(anotherProvider.validateAccessToken(token))
                .isFalse();
    }

    @Test
    @DisplayName("Deve rejeitar token com audience diferente")
    void deveRejeitarTokenComAudienceDiferente() {
        String token = provider.generateAccessToken("admin").token();

        JwtTokenProvider anotherProvider =
                new JwtTokenProvider(
                        ACCESS_SECRET,
                        REFRESH_SECRET,
                        ISSUER,
                        "outra-audience",
                        ACCESS_EXPIRATION,
                        REFRESH_EXPIRATION
                );

        assertThat(anotherProvider.validateAccessToken(token))
                .isFalse();
    }

    @Test
    @DisplayName("Deve incluir somente os claims esperados no access token")
    void deveIncluirClaimsEsperadosNoAccessToken() {
        String token = provider.generateAccessToken("admin").token();

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(
                        Base64.getDecoder().decode(ACCESS_SECRET)
                ))
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("admin");
        assertThat(claims.get("jti", String.class)).isNotBlank();
        assertThat(claims.get("type", String.class)).isEqualTo("access");

        assertThat(claims.keySet())
                .containsExactlyInAnyOrder("sub", "jti", "type", "iat", "exp", "iss", "aud");
    }

    @Test
    @DisplayName("Deve incluir somente os claims esperados no refresh token")
    void deveIncluirClaimsEsperadosNoRefreshToken() {
        String token = provider.generateRefreshToken("admin").token();

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(
                        Base64.getDecoder().decode(REFRESH_SECRET)
                ))
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo("admin");
        assertThat(claims.get("jti", String.class)).isNotBlank();
        assertThat(claims.get("type", String.class)).isEqualTo("refresh");

        assertThat(claims.keySet())
                .containsExactlyInAnyOrder("sub", "jti", "type", "iat", "exp", "iss", "aud");
    }
}
