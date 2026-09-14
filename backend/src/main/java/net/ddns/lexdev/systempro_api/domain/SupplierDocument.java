package net.ddns.lexdev.systempro_api.domain;

import java.time.LocalDate;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


// Documentos e Certidões
@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SupplierDocument {
    private String tipoDocumento; // Ex: Contrato Social, Certidão Negativa, Inscrição Municipal
    private String numeroOuUrl;
    private LocalDate dataValidade;
}
