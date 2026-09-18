package net.ddns.lexdev.systempro_api.domain;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.ddns.lexdev.systempro_api.enums.TipoPessoa;

@Entity
@Table(name = "tb_person")
@SQLDelete(sql = "UPDATE tb_person SET active = false WHERE id = ? AND version = ?")
@SQLRestriction("active = true")
@Getter
@Setter
@NoArgsConstructor
public class Person extends AuditableEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pessoa", nullable = false, length = 2)
    private TipoPessoa tipoPessoa;

    @NotBlank
    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "nome_fantasia", length = 150)
    private String nomeFantasia;

    @NotBlank
    @Column(name = "cpf_cnpj", length = 14, nullable = false, unique = true)
    private String cpfCnpj;

    @Column(name = "rg_ie", length = 20)
    private String rgIe;

    @Email
    @Column(length = 255)
    private String email;

    @Column(length = 11)
    private String phone;

    @Column(length = 8)
    private String cep;

    @Column(length = 150)
    private String logradouro;

    @Column(length = 20)
    private String numero;

    @Column(length = 100)
    private String complemento;

    @Column(length = 100)
    private String bairro;

    @Column(length = 100)
    private String cidade;

    @Column(length = 2)
    private String uf;

    @Column(nullable = false)
    private boolean active = true;

    @PrePersist
    @PreUpdate
    private void normalizeFields() {
        if (cpfCnpj != null) {
            cpfCnpj = cpfCnpj.replaceAll("\\D", "");
        }

        if (phone != null) {
            phone = phone.replaceAll("\\D", "");
        }

        if (cep != null) {
            cep = cep.replaceAll("\\D", "");
        }

        if (rgIe != null) {
            rgIe = rgIe.replaceAll("\\D", "");
        }

        if (uf != null) {
            uf = uf.trim().toUpperCase();
        }
    }
}
