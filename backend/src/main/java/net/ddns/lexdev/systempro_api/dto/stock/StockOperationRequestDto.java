package net.ddns.lexdev.systempro_api.dto.stock;
import java.math.BigDecimal; import java.time.LocalDate; import jakarta.validation.constraints.*;
public record StockOperationRequestDto(
 @NotNull @DecimalMin("0.000001") BigDecimal quantity,
 @NotBlank @Size(max=500) String reason,
 @NotBlank @Size(max=120) String operationReference,
 @Size(max=100) String lotCode,
 LocalDate manufactureDate,
 LocalDate expirationDate
) {}