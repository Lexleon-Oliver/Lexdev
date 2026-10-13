package net.ddns.lexdev.systempro_api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "tb_stock_settings")
public class StockSettings {
    public static final short SINGLETON_ID = 1;

    @Id
    private Short id = SINGLETON_ID;

    @Column(name = "min_sellable_shelf_life_days", nullable = false)
    private int minSellableShelfLifeDays = 1;

    @Version
    @Column(nullable = false)
    private Long version;

    public Short getId() { return id; }
    public int getMinSellableShelfLifeDays() { return minSellableShelfLifeDays; }
    public void setMinSellableShelfLifeDays(int value) { this.minSellableShelfLifeDays = value; }
}