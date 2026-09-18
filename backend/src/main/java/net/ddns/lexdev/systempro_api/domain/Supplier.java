package net.ddns.lexdev.systempro_api.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tb_supplier")
@SQLDelete(sql = "UPDATE tb_supplier SET active = false WHERE id = ? AND version = ?")
@SQLRestriction("active = true")
@Getter
@Setter
@NoArgsConstructor
public class Supplier extends AuditableEntity {

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

    // =========================
    // COMERCIAL
    // =========================

    @Column(name = "condicao_pagamento_padrao", length = 100)
    private String condicaoPagamentoPadrao;

    @Column(name = "prazo_entrega_dias")
    private Integer prazoEntregaDias;

    @Column(
        name = "valor_minimo_pedido",
        precision = 19,
        scale = 2
    )
    private BigDecimal valorMinimoPedido;

    @Column(length = 100)
    private String categoria;

    @Column(
        name = "observacoes_comerciais",
        columnDefinition = "TEXT"
    )
    private String observacoesComerciais;

    // =========================
    // DADOS BANCÁRIOS
    // =========================

    @jakarta.persistence.Embedded
    private BankDetails bankDetails;

    // =========================
    // CONTATOS ADICIONAIS
    // =========================

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "tb_supplier_contacts",
        joinColumns = @JoinColumn(name = "supplier_id")
    )
    private List<SupplierContact> contatos = new ArrayList<>();

    // =========================
    // DOCUMENTOS
    // =========================

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "tb_supplier_documents",
        joinColumns = @JoinColumn(name = "supplier_id")
    )
    private List<SupplierDocument> documentos = new ArrayList<>();

    @Column(nullable = false)
    private boolean active = true;

    public Supplier(Person person) {
        this.person = person;
    }
}
