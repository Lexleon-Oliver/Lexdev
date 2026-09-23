package net.ddns.lexdev.systempro_api.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException{

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        String json = """
            {
                "timestamp": "%s",
                "status": 401,
                "error": "Não Autorizado",
                "message": "Token inválido ou expirado",
                "path": "%s"
            }
            """.formatted(
                Instant.now(),
                request.getRequestURI()
            );

        response.getWriter().write(json);
    }
}