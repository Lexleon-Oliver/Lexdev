package net.ddns.lexdev.systempro_api.dto.report;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinancialDailyDto(LocalDate date, long sales, BigDecimal total) {}