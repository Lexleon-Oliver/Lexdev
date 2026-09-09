package net.ddns.lexdev.systempro_api.domain;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tb_client")
@SQLDelete(sql = "UPDATE tb_client SET active = false WHERE id = ?")
@SQLRestriction("active = true")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tipo_pessoa", length = 2, nullable = false)
    private String tipoPessoa;

    @Column(nullable = false)
    private String name;

    @Column(name = "nome_fantasia")
    private String nomeFantasia;

    @Column(name = "cpf_cnpj", length = 14, nullable = false, unique = true)
    private String cpfCnpj;

    @Column(name = "rg_ie", length = 20)
    private String rgIe;

    @Column(nullable = false)
    private String email;

    @Column(length = 11)
    private String phone;

    @Column(length = 8)
    private String cep;

    private String logradouro;
    private String numero;
    private String complemento;
    private String bairro;
    private String cidade;

    @Column(length = 2)
    private String uf;

    @Column(nullable = false)
    private Boolean active = true;

    @PrePersist
    @PreUpdate
    public void sanitizeFields() {
        if (this.cpfCnpj != null) {
            this.cpfCnpj = this.cpfCnpj.replaceAll("\\D", "");
        }
        if (this.phone != null) {
            this.phone = this.phone.replaceAll("\\D", "");
        }
        if (this.cep != null) {
            this.cep = this.cep.replaceAll("\\D", "");
        }
        if (this.rgIe != null) {
            this.rgIe = this.rgIe.replaceAll("\\D", "");
        }
    }
}