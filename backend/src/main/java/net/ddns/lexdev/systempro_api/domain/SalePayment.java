package net.ddns.lexdev.systempro_api.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import net.ddns.lexdev.systempro_api.enums.PaymentMethod;

@Entity
@Table(name = "tb_sale_payment")
public class SalePayment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, foreignKey = @ForeignKey(name = "fk_sale_payment_sale"))
    private Sale sale;
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    @Column(name = "card_brand", length = 50)
    private String cardBrand;
    @Column(name = "authorization_code", length = 100)
    private String authorizationCode;
    public Long getId() { return id; }
    public Sale getSale() { return sale; }
    public void setSale(Sale v) { sale = v; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod v) { paymentMethod = v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { amount = v; }
    public String getCardBrand() { return cardBrand; }
    public void setCardBrand(String v) { cardBrand = v; }
    public String getAuthorizationCode() { return authorizationCode; }
    public void setAuthorizationCode(String v) { authorizationCode = v; }
}
