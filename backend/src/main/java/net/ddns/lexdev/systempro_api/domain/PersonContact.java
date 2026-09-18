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
import net.ddns.lexdev.systempro_api.enums.ContactType;

@Entity
@Table(
    name = "tb_person_contact",
    indexes = {
        @Index(
            name = "idx_person_contact_person",
            columnList = "person_id"
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
public class PersonContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "person_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_person_contact_person")
    )
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContactType type;

    @Column(nullable = false, length = 255)
    private String value;

    @Column(nullable = false)
    private boolean principal = false;

    @Column(length = 150)
    private String description;

    @PrePersist
    @PreUpdate
    private void normalizeFields() {
        if (value != null) {
            value = switch (type) {
                case EMAIL -> value.trim().toLowerCase();
                case TELEFONE, CELULAR, WHATSAPP ->
                    value.replaceAll("\\D", "");
            };
        }
    }
}
