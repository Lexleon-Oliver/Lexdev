package net.ddns.lexdev.systempro_api.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardDailyDto(LocalDate date, long sales, BigDecimal total) {}