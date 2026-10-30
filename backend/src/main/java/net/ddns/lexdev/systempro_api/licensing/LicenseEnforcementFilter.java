package net.ddns.lexdev.systempro_api.licensing;

import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class LicenseEnforcementFilter extends OncePerRequestFilter {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final String[] ALWAYS_ALLOWED_PREFIXES = {
        "/auth/",
        "/backups",
        "/support/",
        "/licensing/",
        "/error",
        "/v3/api-docs",
        "/swagger-ui"
    };

    private final LicenseStateService licenseStateService;
    private final JsonMapper objectMapper;

    public LicenseEnforcementFilter(
        LicenseStateService licenseStateService,
        JsonMapper objectMapper
    ) {
        this.licenseStateService = licenseStateService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (READ_METHODS.contains(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        for (String prefix : ALWAYS_ALLOWED_PREFIXES) {
            if (matchesPathOrChild(path, prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPathOrChild(String path, String prefix) {
        if (prefix.endsWith("/")) {
            String base = prefix.substring(0, prefix.length() - 1);
            return path.equals(base) || path.startsWith(prefix);
        }
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        LicenseDecision decision = licenseStateService.currentDecision();
        if (decision.allowsCommercialWrites()) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(423);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
            response.getOutputStream(),
            Map.of(
                "error", Map.of(
                    "code", decision.code(),
                    "message", decision.message()
                ),
                "licenseMode", decision.mode().name()
            )
        );
    }
}
