package net.ddns.lexdev.systempro_api.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.ddns.lexdev.systempro_api.enums.TipoContaBancaria;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BankDetails {

    private String banco;

    private String agencia;

    private String conta;

    @Enumerated(EnumType.STRING)
    @jakarta.persistence.Column(name = "tipo_conta", length = 20)
    private TipoContaBancaria tipoConta;

    @jakarta.persistence.Column(name = "chave_pix", length = 100)
    private String chavePix;
}
