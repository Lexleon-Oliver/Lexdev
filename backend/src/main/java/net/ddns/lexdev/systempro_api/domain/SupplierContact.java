package net.ddns.lexdev.systempro_api.domain;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SupplierContact {
    private String nome;
    private String cargo;
    private String email;
    private String telefone;
    private String setor; // Ex: Compras, Financeiro, Pós-venda
}
