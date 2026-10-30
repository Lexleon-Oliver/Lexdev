package net.ddns.lexdev.systempro_api.licensing;

import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LicenseEnforcementFilterTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void restrictedBlocksCommercialWrite() throws Exception {
        LicenseStateService state = mock(LicenseStateService.class);
        when(state.currentDecision()).thenReturn(new LicenseDecision(
            LicenseMode.RESTRICTED,
            "LICENSE_OFFLINE_GRACE_EXPIRED",
            "regularize",
            "installation-id",
            "binding-id",
            Instant.now().minusSeconds(100),
            Instant.now().minusSeconds(10)
        ));
        LicenseEnforcementFilter filter = new LicenseEnforcementFilter(state, objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/sales");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(423);
        assertThat(response.getContentAsString()).contains("LICENSE_OFFLINE_GRACE_EXPIRED");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void restrictedStillAllowsBackupAndReads() throws Exception {
        LicenseStateService state = mock(LicenseStateService.class);
        when(state.currentDecision()).thenReturn(new LicenseDecision(
            LicenseMode.RESTRICTED, "X", "x", null, null, null, null
        ));
        LicenseEnforcementFilter filter = new LicenseEnforcementFilter(state, objectMapper);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest backup = new MockHttpServletRequest("POST", "/backups");
        MockHttpServletResponse backupResponse = new MockHttpServletResponse();
        filter.doFilter(backup, backupResponse, chain);
        verify(chain).doFilter(backup, backupResponse);

        MockHttpServletRequest read = new MockHttpServletRequest("GET", "/sales");
        MockHttpServletResponse readResponse = new MockHttpServletResponse();
        filter.doFilter(read, readResponse, chain);
        verify(chain).doFilter(read, readResponse);
    }
}
