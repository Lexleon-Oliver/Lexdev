package net.ddns.lexdev.systempro_api.domain;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class BankDetails {
    private String banco;
    private String agencia;
    private String conta;
    private String tipoConta; // Ex: CORRENTE, POUPANCA
    private String chavePix;
}
