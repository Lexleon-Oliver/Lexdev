package net.ddns.lexdev.systempro_api.domain;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
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

    @NotBlank
    @Column(name = "cpf_cnpj", length = 14, nullable = false, unique = true)
    private String cpfCnpj;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(
        mappedBy = "person",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @Getter
    private List<PersonContact> contacts = new ArrayList<>();
    

    @OneToOne(
        mappedBy = "person",
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private IndividualPerson individualPerson;

    @OneToOne(
        mappedBy = "person",
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private LegalEntity legalEntity;

    @OneToMany(
        mappedBy = "person",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    private List<PersonAddress> addresses = new ArrayList<>();

    @PrePersist
    @PreUpdate
    private void normalizeFields() {
        if (cpfCnpj != null) {
            cpfCnpj = cpfCnpj.replaceAll("\\D", "");
        }
    }

    public void addContact(PersonContact contact) {
        contacts.add(contact);
        contact.setPerson(this);
    }

    public void removeContact(PersonContact contact) {
        contacts.remove(contact);
        contact.setPerson(null);
    }

    public void addAddress(PersonAddress address) {
        addresses.add(address);
        address.setPerson(this);
    }

    public void removeAddress(PersonAddress address) {
        addresses.remove(address);
        address.setPerson(null);
    }
}