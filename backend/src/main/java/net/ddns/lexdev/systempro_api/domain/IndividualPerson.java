package net.ddns.lexdev.systempro_api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "tb_individual_person",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_individual_person_person",
            columnNames = "person_id"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class IndividualPerson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "person_id",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_individual_person_person")
    )
    private Person person;

    @Column(length = 20)
    private String rg;

    @PrePersist
    @PreUpdate
    private void normalizeFields() {
        if (rg != null) {
            rg = rg.replaceAll("\\D", "");
        }
    }
}