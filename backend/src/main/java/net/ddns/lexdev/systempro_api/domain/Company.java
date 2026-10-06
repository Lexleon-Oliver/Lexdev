package net.ddns.lexdev.systempro_api.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "tb_company")
public class Company extends AuditableEntity {

    @OneToOne(
        fetch = FetchType.LAZY,
        optional = false,
        cascade = {CascadeType.PERSIST, CascadeType.MERGE}
    )
    @JoinColumn(
        name = "person_id",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_company_person")
    )
    private Person person;

    public Company() {
    }

    public Company(Person person) {
        this.person = person;
    }

    public Person getPerson() {
        return person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }

}
