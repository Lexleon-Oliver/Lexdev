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
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tb_supplier")
@SQLDelete(sql = "UPDATE tb_supplier SET active = false WHERE id = ?")
@SQLRestriction("active = true")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Vinculo com a base comum (Person)
    @OneToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id", nullable = false, unique = true)
    private Person person;

    // === COMERCIAL ===
    private String condicaoPagamentoPadrao; // Ex: 30/60/90 dias
    private Integer prazoEntregaDias;
    private BigDecimal valorMinimoPedido;
    private String categoria; // Ex: Matéria-prima, Serviços, Embalagens
    
    @Column(columnDefinition = "TEXT")
    private String observacoesComerciais;

    // === DADOS BANCÁRIOS ===
    @Embedded
    private BankDetails bankDetails;

    // === CONTATOS ADICIONAIS ===
    @ElementCollection
    @CollectionTable(name = "tb_supplier_contacts", joinColumns = @JoinColumn(name = "supplier_id"))
    private List<SupplierContact> contatos = new ArrayList<>();

    // === DOCUMENTOS E CERTIDÕES ===
    @ElementCollection
    @CollectionTable(name = "tb_supplier_documents", joinColumns = @JoinColumn(name = "supplier_id"))
    private List<SupplierDocument> documentos = new ArrayList<>();

    @Column(nullable = false)
    private Boolean active = true;
}
