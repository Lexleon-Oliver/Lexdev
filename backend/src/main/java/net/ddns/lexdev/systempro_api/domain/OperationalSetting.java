package net.ddns.lexdev.systempro_api.domain;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "tb_operational_settings")
public class OperationalSetting {
    @Id
    @Column(name = "setting_key", length = 80, nullable = false)
    private String key;

    @Column(name = "int_value", nullable = false)
    private int intValue;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 50, nullable = false)
    private String updatedBy;

    @Version
    @Column(nullable = false)
    private Long version;

    protected OperationalSetting() {}

    public OperationalSetting(String key, int intValue, String actor, Instant at) {
        this.key = key;
        update(intValue, actor, at);
    }

    public void update(int value, String actor, Instant at) {
        this.intValue = value;
        this.updatedBy = actor;
        this.updatedAt = at;
    }

    public String getKey() { return key; }
    public int getIntValue() { return intValue; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public Long getVersion() { return version; }
}
