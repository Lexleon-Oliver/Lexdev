package net.ddns.lexdev.systempro_api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.ddns.lexdev.systempro_api.enums.AddressType;

@Entity
@Table(
    name = "tb_person_address",
    indexes = {
        @Index(
            name = "idx_person_address_person",
            columnList = "person_id"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class PersonAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "person_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_person_address_person")
    )
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AddressType type;

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
    private boolean principal = false;

    @PrePersist
    @PreUpdate
    private void normalizeFields() {
        if (cep != null) {
            cep = cep.replaceAll("\\D", "");
        }

        if (uf != null) {
            uf = uf.trim().toUpperCase();
        }
    }
}