package net.ddns.lexdev.systempro_api.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import net.ddns.lexdev.systempro_api.enums.SaleStatus;

@Entity
@Table(name = "tb_sale")
public class Sale extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fiscal_establishment_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_sale_fiscal_establishment"))
    private FiscalEstablishment fiscalEstablishment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", foreignKey = @ForeignKey(name = "fk_sale_client"))
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_sale_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SaleStatus status = SaleStatus.AGUARDANDO_FISCAL;

    @Column(name = "sale_at", nullable = false)
    private Instant saleAt = Instant.now();
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal = BigDecimal.ZERO;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal discount = BigDecimal.ZERO;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal total = BigDecimal.ZERO;
    @Column(name = "consumer_cpf_cnpj", length = 14)
    private String consumerCpfCnpj;
    @Column(length = 500)
    private String note;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SaleItem> items = new ArrayList<>();
    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SalePayment> payments = new ArrayList<>();

    public Sale() {}
    public void addItem(SaleItem item) { items.add(item); item.setSale(this); }
    public void addPayment(SalePayment payment) { payments.add(payment); payment.setSale(this); }
    public FiscalEstablishment getFiscalEstablishment() { return fiscalEstablishment; }
    public void setFiscalEstablishment(FiscalEstablishment v) { fiscalEstablishment = v; }
    public Client getClient() { return client; }
    public void setClient(Client v) { client = v; }
    public User getUser() { return user; }
    public void setUser(User v) { user = v; }
    public SaleStatus getStatus() { return status; }
    public void setStatus(SaleStatus v) { status = v; }
    public Instant getSaleAt() { return saleAt; }
    public void setSaleAt(Instant v) { saleAt = v; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal v) { subtotal = v; }
    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal v) { discount = v; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal v) { total = v; }
    public String getConsumerCpfCnpj() { return consumerCpfCnpj; }
    public void setConsumerCpfCnpj(String v) { consumerCpfCnpj = v; }
    public String getNote() { return note; }
    public void setNote(String v) { note = v; }
    public List<SaleItem> getItems() { return items; }
    public List<SalePayment> getPayments() { return payments; }
}
