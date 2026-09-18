package net.ddns.lexdev.systempro_api.domain;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tb_client")
@SQLDelete(sql = "UPDATE tb_client SET active = false WHERE id = ? AND version = ?")
@SQLRestriction("active = true")
@Getter
@Setter
@NoArgsConstructor
public class Client extends AuditableEntity {

    @OneToOne(
        cascade = {CascadeType.PERSIST, CascadeType.MERGE},
        fetch = FetchType.LAZY,
        optional = false
    )
    @JoinColumn(
        name = "person_id",
        nullable = false,
        unique = true
    )
    private Person person;

    @Column(nullable = false)
    private boolean active = true;

    public Client(Person person) {
        this.person = person;
    }
}