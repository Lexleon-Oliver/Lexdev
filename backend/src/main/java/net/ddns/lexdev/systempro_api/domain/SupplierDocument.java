package net.ddns.lexdev.systempro_api.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tb_supplier_documents")
@Getter
@Setter
@NoArgsConstructor
@SQLDelete(sql = "UPDATE tb_supplier_documents SET active = false WHERE id = ? AND version = ?")
@SQLRestriction("active = true")
public class SupplierDocument extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @Column(name = "tipo_documento", nullable = false, length = 100)
    private String tipoDocumento;

    @Column(name = "numero_documento", length = 100)
    private String numeroDocumento;

    @Column(name = "data_emissao")
    private LocalDate dataEmissao;

    @Column(name = "data_validade")
    private LocalDate dataValidade;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @OneToMany(
        mappedBy = "document",
        fetch = FetchType.LAZY,
        orphanRemoval = false
    )
    @OrderBy("versionNumber DESC")
    private List<SupplierDocumentVersion> versions = new ArrayList<>();

    public SupplierDocument(
        Supplier supplier,
        String tipoDocumento,
        String numeroDocumento,
        LocalDate dataEmissao,
        LocalDate dataValidade
    ) {
        this.supplier = supplier;
        this.tipoDocumento = tipoDocumento;
        this.numeroDocumento = numeroDocumento;
        this.dataEmissao = dataEmissao;
        this.dataValidade = dataValidade;
    }
}