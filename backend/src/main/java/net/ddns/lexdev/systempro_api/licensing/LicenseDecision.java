package net.ddns.lexdev.systempro_api.licensing;

import java.time.Instant;

public record LicenseDecision(
    LicenseMode mode,
    String code,
    String message,
    String installationId,
    String bindingId,
    Instant leaseValidUntil,
    Instant offlineGraceUntil
) {
    public boolean allowsCommercialWrites() {
        return mode == LicenseMode.DISABLED
            || mode == LicenseMode.FULL
            || mode == LicenseMode.GRACE;
    }
}
