package net.ddns.lexdev.systempro_api.licensing;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/licensing")
public class LicenseStatusController {

    private final LicenseStateService licenseStateService;

    public LicenseStatusController(LicenseStateService licenseStateService) {
        this.licenseStateService = licenseStateService;
    }

    @GetMapping("/status")
    public LicenseStatusResponse status() {
        LicenseDecision decision = licenseStateService.currentDecision();
        return new LicenseStatusResponse(
            decision.mode().name(),
            decision.code(),
            decision.message(),
            decision.installationId(),
            decision.bindingId(),
            decision.leaseValidUntil(),
            decision.offlineGraceUntil()
        );
    }

    public record LicenseStatusResponse(
        String mode,
        String code,
        String message,
        String installationId,
        String bindingId,
        Instant leaseValidUntil,
        Instant offlineGraceUntil
    ) {
    }
}
