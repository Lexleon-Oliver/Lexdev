package net.ddns.lexdev.systempro_api.dto;

import java.math.BigDecimal;

import net.ddns.lexdev.systempro_api.domain.FiscalProductProfile;

public record ProductFiscalProfileResponseDto(
    Long id, Long productId, String cfop, String icmsCstCsosn, String pisCst, String cofinsCst,
    BigDecimal icmsRate, BigDecimal pisRate, BigDecimal cofinsRate,
    String ibsCbsCst, String cClassTrib, BigDecimal ibsRate, BigDecimal cbsRate,
    String additionalInformation, boolean active
) {
    public static ProductFiscalProfileResponseDto fromEntity(FiscalProductProfile p) {
        return new ProductFiscalProfileResponseDto(p.getId(), p.getProduct().getId(), p.getCfop(), p.getIcmsCstCsosn(),
            p.getPisCst(), p.getCofinsCst(), p.getIcmsRate(), p.getPisRate(), p.getCofinsRate(),
            p.getIbsCbsCst(), p.getCClassTrib(), p.getIbsRate(), p.getCbsRate(), p.getAdditionalInformation(), p.isActive());
    }
}
