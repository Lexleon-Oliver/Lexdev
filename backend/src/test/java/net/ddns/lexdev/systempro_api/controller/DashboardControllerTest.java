package net.ddns.lexdev.systempro_api.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import net.ddns.lexdev.systempro_api.dto.dashboard.DashboardInsightResponseDto;
import net.ddns.lexdev.systempro_api.dto.dashboard.DashboardInsightResponseDto.Status;
import net.ddns.lexdev.systempro_api.service.DashboardInsightService;
import net.ddns.lexdev.systempro_api.service.DashboardService;

class DashboardControllerTest {

    @Test
    void deveMapearEndpointDeInsights() throws Exception {
        DashboardService dashboardService =
            mock(DashboardService.class);

        DashboardInsightService insightService =
            mock(DashboardInsightService.class);

        when(insightService.insight()).thenReturn(
            new DashboardInsightResponseDto(
                Status.GENERATED,
                "Insight de teste."
            )
        );

        DashboardController controller =
            new DashboardController(
                dashboardService,
                insightService
            );

        MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(controller)
            .build();

        mockMvc.perform(get("/dashboard/insights"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("GENERATED"))
            .andExpect(jsonPath("$.insight").value("Insight de teste."));
    }
}
