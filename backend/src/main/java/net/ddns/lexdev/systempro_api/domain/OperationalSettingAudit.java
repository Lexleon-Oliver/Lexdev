package net.ddns.lexdev.systempro_api.domain;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tb_operational_setting_audit")
public class OperationalSettingAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, length = 80)
    private String key;
    @Column(name = "old_int_value")
    private Integer oldValue;
    @Column(name = "new_int_value")
    private Integer newValue;
    @Column(name = "old_effective_value", nullable = false)
    private int oldEffectiveValue;
    @Column(name = "new_effective_value", nullable = false)
    private int newEffectiveValue;
    @Column(nullable = false, length = 50)
    private String actor;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected OperationalSettingAudit() {}

    public OperationalSettingAudit(String key, Integer oldValue, Integer newValue,
                                   int oldEffectiveValue, int newEffectiveValue, String actor, Instant occurredAt) {
        this.key = key;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.oldEffectiveValue = oldEffectiveValue;
        this.newEffectiveValue = newEffectiveValue;
        this.actor = actor;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public String getKey() { return key; }
    public Integer getOldValue() { return oldValue; }
    public Integer getNewValue() { return newValue; }
    public int getOldEffectiveValue() { return oldEffectiveValue; }
    public int getNewEffectiveValue() { return newEffectiveValue; }
    public String getActor() { return actor; }
    public Instant getOccurredAt() { return occurredAt; }
}
