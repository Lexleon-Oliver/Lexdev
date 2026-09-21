package net.ddns.lexdev.systempro_api.service;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import net.ddns.lexdev.systempro_api.config.JwtTokenProvider;
import net.ddns.lexdev.systempro_api.domain.RefreshToken;
import net.ddns.lexdev.systempro_api.domain.User;
import net.ddns.lexdev.systempro_api.dto.AuthResponse;
import net.ddns.lexdev.systempro_api.dto.LoginRequest;
import net.ddns.lexdev.systempro_api.dto.RefreshTokenRequest;
import net.ddns.lexdev.systempro_api.repository.RefreshTokenRepository;
import net.ddns.lexdev.systempro_api.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private User user;

    @InjectMocks
    private AuthService authService;

    private static final String USERNAME = "admin";
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final String JTI = "refresh-jti";

    private static final Instant FUTURE =
            Instant.now().plusSeconds(3600);

    // ---------------------------------------------------------
    // LOGIN
    // ---------------------------------------------------------

    @Test
    void login_ComCredenciaisValidas_DeveEmitirTokens() {
        LoginRequest request =
                new LoginRequest(USERNAME, "senha123");

        when(userRepository.findByUsernameAndActiveTrue(USERNAME))
                .thenReturn(Optional.of(user));

        when(user.getUsername()).thenReturn(USERNAME);

        when(jwtTokenProvider.generateAccessToken(USERNAME))
                .thenReturn(new JwtTokenProvider.TokenHolder(
                        "access-token", "access-jti", FUTURE
                ));

        when(jwtTokenProvider.generateRefreshToken(USERNAME))
                .thenReturn(new JwtTokenProvider.TokenHolder(
                        REFRESH_TOKEN, JTI, FUTURE
                ));

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("access-token", response.accessToken());
        assertEquals(REFRESH_TOKEN, response.refreshToken());
        assertEquals("Bearer", response.tokenType());

        verify(authenticationManager).authenticate(any(
                UsernamePasswordAuthenticationToken.class
        ));

        verify(refreshTokenRepository).save(argThat(token ->
                token.getJti().equals(JTI)
                        && token.getUsername().equals(USERNAME)
                        && !token.isRevoked()
        ));
    }

    @Test
    void login_QuandoAutenticacaoFalha_DevePropagarExcecao() {
        LoginRequest request =
                new LoginRequest(USERNAME, "senha-errada");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Credenciais inválidas"));

        assertThrows(
                BadCredentialsException.class,
                () -> authService.login(request)
        );

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void login_QuandoUsuarioNaoEstaAtivo_DeveLancarExcecao() {
        LoginRequest request =
                new LoginRequest(USERNAME, "senha123");

        when(userRepository.findByUsernameAndActiveTrue(USERNAME))
                .thenReturn(Optional.empty());

        assertThrows(
                BadCredentialsException.class,
                () -> authService.login(request)
        );

        verifyNoInteractions(refreshTokenRepository);
    }

    // ---------------------------------------------------------
    // REFRESH TOKEN
    // ---------------------------------------------------------

    @Test
    void refreshToken_ComTokenValido_DeveRevogarAnteriorEEmitirNovos() {
        RefreshTokenRequest request =
                new RefreshTokenRequest(REFRESH_TOKEN);

        RefreshToken storedToken =
                new RefreshToken(JTI, USERNAME, FUTURE);

        when(jwtTokenProvider.validateRefreshToken(REFRESH_TOKEN))
                .thenReturn(true);

        when(jwtTokenProvider.extractJtiFromRefreshToken(REFRESH_TOKEN))
                .thenReturn(JTI);

        when(refreshTokenRepository.findByJtiForUpdate(JTI))
                .thenReturn(Optional.of(storedToken));

        when(userRepository.findByUsernameAndActiveTrue(USERNAME))
                .thenReturn(Optional.of(user));

        when(user.getUsername()).thenReturn(USERNAME);

        when(jwtTokenProvider.generateAccessToken(USERNAME))
                .thenReturn(new JwtTokenProvider.TokenHolder(
                        "new-access-token", "new-access-jti", FUTURE
                ));

        when(jwtTokenProvider.generateRefreshToken(USERNAME))
                .thenReturn(new JwtTokenProvider.TokenHolder(
                        "new-refresh-token", "new-refresh-jti", FUTURE
                ));

        AuthResponse response = authService.refreshToken(request);

        assertNotNull(response);
        assertEquals("new-access-token", response.accessToken());
        assertEquals("new-refresh-token", response.refreshToken());

        assertTrue(storedToken.isRevoked());

        verify(refreshTokenRepository).save(storedToken);

        verify(refreshTokenRepository).save(argThat(token ->
                token.getJti().equals("new-refresh-jti")
                        && token.getUsername().equals(USERNAME)
                        && !token.isRevoked()
        ));
    }

    @Test
    void refreshToken_ComJwtInvalido_DeveLancarExcecao() {
        RefreshTokenRequest request =
                new RefreshTokenRequest(REFRESH_TOKEN);

        when(jwtTokenProvider.validateRefreshToken(REFRESH_TOKEN))
                .thenReturn(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> authService.refreshToken(request)
        );

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void refreshToken_QuandoTokenNaoExisteNoBanco_DeveLancarExcecao() {
        RefreshTokenRequest request =
                new RefreshTokenRequest(REFRESH_TOKEN);

        when(jwtTokenProvider.validateRefreshToken(REFRESH_TOKEN))
                .thenReturn(true);

        when(jwtTokenProvider.extractJtiFromRefreshToken(REFRESH_TOKEN))
                .thenReturn(JTI);

        when(refreshTokenRepository.findByJtiForUpdate(JTI))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> authService.refreshToken(request)
        );
    }

    @Test
    void refreshToken_QuandoTokenJaFoiRevogado_DeveLancarExcecao() {
        RefreshTokenRequest request =
                new RefreshTokenRequest(REFRESH_TOKEN);

        RefreshToken storedToken =
                new RefreshToken(JTI, USERNAME, FUTURE);

        storedToken.setRevoked(true);

        when(jwtTokenProvider.validateRefreshToken(REFRESH_TOKEN))
                .thenReturn(true);

        when(jwtTokenProvider.extractJtiFromRefreshToken(REFRESH_TOKEN))
                .thenReturn(JTI);

        when(refreshTokenRepository.findByJtiForUpdate(JTI))
                .thenReturn(Optional.of(storedToken));

        assertThrows(
                IllegalArgumentException.class,
                () -> authService.refreshToken(request)
        );

        verify(userRepository, never())
                .findByUsernameAndActiveTrue(anyString());
    }

    @Test
    void refreshToken_QuandoTokenExpirouNoBanco_DeveLancarExcecao() {
        RefreshTokenRequest request =
                new RefreshTokenRequest(REFRESH_TOKEN);

        RefreshToken storedToken = new RefreshToken(
                JTI,
                USERNAME,
                Instant.now().minusSeconds(60)
        );

        when(jwtTokenProvider.validateRefreshToken(REFRESH_TOKEN))
                .thenReturn(true);

        when(jwtTokenProvider.extractJtiFromRefreshToken(REFRESH_TOKEN))
                .thenReturn(JTI);

        when(refreshTokenRepository.findByJtiForUpdate(JTI))
                .thenReturn(Optional.of(storedToken));

        assertThrows(
                IllegalArgumentException.class,
                () -> authService.refreshToken(request)
        );

        verify(userRepository, never())
                .findByUsernameAndActiveTrue(anyString());
    }

    @Test
    void refreshToken_QuandoUsuarioEstaInativo_DeveLancarExcecao() {
        RefreshTokenRequest request =
                new RefreshTokenRequest(REFRESH_TOKEN);

        RefreshToken storedToken =
                new RefreshToken(JTI, USERNAME, FUTURE);

        when(jwtTokenProvider.validateRefreshToken(REFRESH_TOKEN))
                .thenReturn(true);

        when(jwtTokenProvider.extractJtiFromRefreshToken(REFRESH_TOKEN))
                .thenReturn(JTI);

        when(refreshTokenRepository.findByJtiForUpdate(JTI))
                .thenReturn(Optional.of(storedToken));

        when(userRepository.findByUsernameAndActiveTrue(USERNAME))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> authService.refreshToken(request)
        );

        // O token antigo não deve ser revogado se a emissão
        // de novos tokens não puder prosseguir.
        assertFalse(storedToken.isRevoked());

        verify(refreshTokenRepository, never()).save(any());
    }

    // ---------------------------------------------------------
    // LOGOUT
    // ---------------------------------------------------------

    @Test
    void logout_ComTokenValido_DeveRevogarRefreshToken() {
        RefreshTokenRequest request =
                new RefreshTokenRequest(REFRESH_TOKEN);

        RefreshToken storedToken =
                new RefreshToken(JTI, USERNAME, FUTURE);

        when(jwtTokenProvider.validateRefreshToken(REFRESH_TOKEN))
                .thenReturn(true);

        when(jwtTokenProvider.extractJtiFromRefreshToken(REFRESH_TOKEN))
                .thenReturn(JTI);

        when(refreshTokenRepository.findByJtiForUpdate(JTI))
                .thenReturn(Optional.of(storedToken));

        authService.logout(request);

        assertTrue(storedToken.isRevoked());

        verify(refreshTokenRepository).save(storedToken);
    }

    @Test
    void logout_ComTokenInvalido_NaoDeveAlterarBanco() {
        RefreshTokenRequest request =
                new RefreshTokenRequest(REFRESH_TOKEN);

        when(jwtTokenProvider.validateRefreshToken(REFRESH_TOKEN))
                .thenReturn(false);

        assertDoesNotThrow(() -> authService.logout(request));

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void logout_QuandoTokenNaoExisteNoBanco_NaoDeveLancarExcecao() {
        RefreshTokenRequest request =
                new RefreshTokenRequest(REFRESH_TOKEN);

        when(jwtTokenProvider.validateRefreshToken(REFRESH_TOKEN))
                .thenReturn(true);

        when(jwtTokenProvider.extractJtiFromRefreshToken(REFRESH_TOKEN))
                .thenReturn(JTI);

        when(refreshTokenRepository.findByJtiForUpdate(JTI))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authService.logout(request));

        verify(refreshTokenRepository, never()).save(any());
    }
}
