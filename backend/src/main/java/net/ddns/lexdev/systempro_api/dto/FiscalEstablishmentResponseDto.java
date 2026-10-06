package net.ddns.lexdev.systempro_api.dto;

import net.ddns.lexdev.systempro_api.domain.FiscalEstablishment;
import net.ddns.lexdev.systempro_api.enums.FiscalEnvironment;
import net.ddns.lexdev.systempro_api.enums.TaxRegime;

public record FiscalEstablishmentResponseDto(
    Long id,
    Long personId,
    String cnpj,
    String legalName,
    String tradeName,
    String stateRegistration,
    String municipalityIbgeCode,
    TaxRegime taxRegime,
    FiscalEnvironment environment,
    int series,
    long nextNumber,
    Integer cscId,
    boolean hasCsc,
    boolean hasCertificate,
    boolean active
) {
    public static FiscalEstablishmentResponseDto fromEntity(FiscalEstablishment e) {
        var legal = e.getPerson().getLegalEntity();
        return new FiscalEstablishmentResponseDto(
            e.getId(), e.getPerson().getId(), e.getPerson().getCpfCnpj(), e.getPerson().getName(),
            legal != null ? legal.getNomeFantasia() : null,
            legal != null ? legal.getInscricaoEstadual() : null,
            e.getMunicipalityIbgeCode(), e.getTaxRegime(), e.getEnvironment(), e.getSeries(), e.getNextNumber(),
            e.getCscId(), e.getEncryptedCsc() != null && !e.getEncryptedCsc().isBlank(),
            e.getCertificateStorageKey() != null && !e.getCertificateStorageKey().isBlank(), e.isActive()
        );
    }
}
