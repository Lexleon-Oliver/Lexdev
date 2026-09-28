package net.ddns.lexdev.systempro_api.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SupplierDocument {

    @Column(
        name = "tipo_documento",
        nullable = false,
        length = 100
    )
    private String tipoDocumento;

    @Column(
        name = "numero_documento",
        length = 100
    )
    private String numeroDocumento;

    @Column(
        name = "data_validade"
    )
    private LocalDate dataValidade;
}
