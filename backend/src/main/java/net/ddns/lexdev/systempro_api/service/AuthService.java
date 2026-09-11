package net.ddns.lexdev.systempro_api.service;

import java.time.Instant;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import net.ddns.lexdev.systempro_api.config.JwtTokenProvider;
import net.ddns.lexdev.systempro_api.domain.RefreshToken;
import net.ddns.lexdev.systempro_api.domain.User;
import net.ddns.lexdev.systempro_api.dto.AuthResponse;
import net.ddns.lexdev.systempro_api.dto.LoginRequest;
import net.ddns.lexdev.systempro_api.dto.RefreshTokenRequest;
import net.ddns.lexdev.systempro_api.repository.RefreshTokenRepository;
import net.ddns.lexdev.systempro_api.repository.UserRepository;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtTokenProvider jwtTokenProvider,
                       UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        User user = userRepository.findByUsernameAndActiveTrue(request.username())
                .orElseThrow(() -> new BadCredentialsException("Usuário inválido ou inativo"));

        return issueNewTokens(user.getUsername());
    }

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String tokenStr = request.refreshToken();

        // 1. Validação matemática/assinatura do JWT
        if (!jwtTokenProvider.validateRefreshToken(tokenStr)) {
            throw new IllegalArgumentException("Refresh token inválido ou expirado");
        }

        String jti = jwtTokenProvider.extractJtiFromRefreshToken(tokenStr);

        // 2. Consulta no banco pelo JTI
        RefreshToken storedToken = refreshTokenRepository.findByJti(jti)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token não encontrado no registro"));

        // 3. Valida se já foi revogado ou se expirou
        if (storedToken.isRevoked() || storedToken.getExpiryDate().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Refresh token revogado ou expirado");
        }

        // 4. Rotação real: Revoga o token atual
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        // 5. Valida se o usuário continua ativo
        User user = userRepository.findByUsernameAndActiveTrue(storedToken.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Usuário inativo ou não encontrado"));

        // 6. Emite o novo par e persiste o novo JTI
        return issueNewTokens(user.getUsername());
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        String tokenStr = request.refreshToken();
        if (jwtTokenProvider.validateRefreshToken(tokenStr)) {
            String jti = jwtTokenProvider.extractJtiFromRefreshToken(tokenStr);
            refreshTokenRepository.findByJti(jti).ifPresent(token -> {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
            });
        }
    }

    private AuthResponse issueNewTokens(String username) {
        JwtTokenProvider.TokenHolder accessHolder = jwtTokenProvider.generateAccessToken(username);
        JwtTokenProvider.TokenHolder refreshHolder = jwtTokenProvider.generateRefreshToken(username);

        // Persiste o JTI do novo Refresh Token
        RefreshToken refreshTokenEntity = new RefreshToken(
                refreshHolder.jti(),
                username,
                Instant.now().plusMillis(refreshHolder.durationMs())
        );
        refreshTokenRepository.save(refreshTokenEntity);

        return new AuthResponse(accessHolder.token(), refreshHolder.token());
    }
}
