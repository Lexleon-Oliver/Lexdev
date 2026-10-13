package net.ddns.lexdev.systempro_api.service.support;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import net.ddns.lexdev.systempro_api.domain.User;
import net.ddns.lexdev.systempro_api.dto.support.DeletedRecordPageResponseDto;
import net.ddns.lexdev.systempro_api.dto.support.DeletedRecordResponseDto;
import net.ddns.lexdev.systempro_api.dto.support.DeletedRecordType;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.service.CurrentUserProvider;

@Service
public class SoftDeleteRecoveryService {

    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserProvider currentUserProvider;

    public SoftDeleteRecoveryService(
        JdbcTemplate jdbcTemplate,
        CurrentUserProvider currentUserProvider
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public DeletedRecordPageResponseDto findDeleted(
        DeletedRecordType type,
        String search,
        int page,
        int size
    ) {
        String q = search == null ? "" : search.trim().toLowerCase();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        return switch (type) {
            case CLIENT -> queryAuditedPage(
                "SELECT c.id,p.name title,p.cpf_cnpj subtitle,c.updated_at FROM tb_client c JOIN tb_person p ON p.id=c.person_id WHERE c.active=false AND (?='' OR LOWER(p.name) LIKE ? OR p.cpf_cnpj LIKE ?) ORDER BY c.updated_at DESC,c.id DESC",
                "SELECT COUNT(*) FROM tb_client c JOIN tb_person p ON p.id=c.person_id WHERE c.active=false AND (?='' OR LOWER(p.name) LIKE ? OR p.cpf_cnpj LIKE ?)",
                type, q, 2, safePage, safeSize
            );
            case SUPPLIER -> queryAuditedPage(
                "SELECT s.id,p.name title,p.cpf_cnpj subtitle,s.updated_at FROM tb_supplier s JOIN tb_person p ON p.id=s.person_id WHERE s.active=false AND (?='' OR LOWER(p.name) LIKE ? OR p.cpf_cnpj LIKE ?) ORDER BY s.updated_at DESC,s.id DESC",
                "SELECT COUNT(*) FROM tb_supplier s JOIN tb_person p ON p.id=s.person_id WHERE s.active=false AND (?='' OR LOWER(p.name) LIKE ? OR p.cpf_cnpj LIKE ?)",
                type, q, 2, safePage, safeSize
            );
            case PRODUCT -> queryAuditedPage(
                "SELECT p.id,p.name title,p.code subtitle,p.updated_at FROM tb_product p WHERE p.active=false AND (?='' OR LOWER(p.name) LIKE ? OR LOWER(p.code) LIKE ?) ORDER BY p.updated_at DESC,p.id DESC",
                "SELECT COUNT(*) FROM tb_product p WHERE p.active=false AND (?='' OR LOWER(p.name) LIKE ? OR LOWER(p.code) LIKE ?)",
                type, q, 2, safePage, safeSize
            );
            case SUPPLIER_DOCUMENT -> queryAuditedPage(
                "SELECT d.id,d.tipo_documento title,COALESCE(d.numero_documento,sp.name) subtitle,d.updated_at FROM tb_supplier_documents d JOIN tb_supplier s ON s.id=d.supplier_id JOIN tb_person sp ON sp.id=s.person_id WHERE d.active=false AND (?='' OR LOWER(d.tipo_documento) LIKE ? OR LOWER(COALESCE(d.numero_documento,'')) LIKE ? OR LOWER(sp.name) LIKE ?) ORDER BY d.updated_at DESC,d.id DESC",
                "SELECT COUNT(*) FROM tb_supplier_documents d JOIN tb_supplier s ON s.id=d.supplier_id JOIN tb_person sp ON sp.id=s.person_id WHERE d.active=false AND (?='' OR LOWER(d.tipo_documento) LIKE ? OR LOWER(COALESCE(d.numero_documento,'')) LIKE ? OR LOWER(sp.name) LIKE ?)",
                type, q, 3, safePage, safeSize
            );
            case USER -> queryUsersPage(q, safePage, safeSize);
        };
    }

    @Transactional
    public void restore(DeletedRecordType type, Long id) {
        DeletedRecordSnapshot snapshot = findDeletedSnapshot(type, id);
        if (snapshot == null) {
            throw new EntityNotFoundException(
                "Registro excluído não encontrado para recuperação: " + id
            );
        }

        int updated = switch (type) {
            case CLIENT -> restoreAudited("tb_client", id);
            case SUPPLIER -> restoreAudited("tb_supplier", id);
            case PRODUCT -> restoreAudited("tb_product", id);
            case SUPPLIER_DOCUMENT -> restoreSupplierDocument(id);
            case USER -> jdbcTemplate.update(
                "UPDATE tb_users SET active=true WHERE id=? AND active=false",
                id
            );
        };

        if (updated == 0) {
            throw new EntityNotFoundException(
                "Registro excluído não encontrado para recuperação: " + id
            );
        }

        registerRecoveryAudit(type, id, snapshot);
    }

    private DeletedRecordSnapshot findDeletedSnapshot(DeletedRecordType type, Long id) {
        return switch (type) {
            case CLIENT -> querySnapshot(
                "SELECT p.name title,p.cpf_cnpj subtitle FROM tb_client c JOIN tb_person p ON p.id=c.person_id WHERE c.id=? AND c.active=false",
                id
            );
            case SUPPLIER -> querySnapshot(
                "SELECT p.name title,p.cpf_cnpj subtitle FROM tb_supplier s JOIN tb_person p ON p.id=s.person_id WHERE s.id=? AND s.active=false",
                id
            );
            case PRODUCT -> querySnapshot(
                "SELECT p.name title,p.code subtitle FROM tb_product p WHERE p.id=? AND p.active=false",
                id
            );
            case SUPPLIER_DOCUMENT -> querySnapshot(
                "SELECT d.tipo_documento title,COALESCE(d.numero_documento,sp.name) subtitle FROM tb_supplier_documents d JOIN tb_supplier s ON s.id=d.supplier_id JOIN tb_person sp ON sp.id=s.person_id WHERE d.id=? AND d.active=false",
                id
            );
            case USER -> querySnapshot(
                "SELECT name title,username subtitle FROM tb_users WHERE id=? AND active=false",
                id
            );
        };
    }

    private DeletedRecordSnapshot querySnapshot(String sql, Long id) {
        return jdbcTemplate.query(
            sql,
            rs -> rs.next()
                ? new DeletedRecordSnapshot(
                    rs.getString("title"),
                    rs.getString("subtitle")
                )
                : null,
            id
        );
    }

    private void registerRecoveryAudit(
        DeletedRecordType type,
        Long id,
        DeletedRecordSnapshot snapshot
    ) {
        User user = currentUserProvider.requireUser();

        jdbcTemplate.update(
            """
            INSERT INTO tb_soft_delete_recovery_audit (
                record_type,
                record_id,
                record_title,
                record_subtitle,
                restored_by,
                restored_by_username,
                restored_at
            ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """,
            type.name(),
            id,
            snapshot.title(),
            snapshot.subtitle(),
            user.getId(),
            user.getUsername()
        );
    }

    private int restoreAudited(String table, Long id) {
        return jdbcTemplate.update(
            "UPDATE " + table + " SET active=true,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND active=false",
            id
        );
    }

    private int restoreSupplierDocument(Long id) {
        Boolean supplierActive = jdbcTemplate.query(
            "SELECT s.active FROM tb_supplier_documents d JOIN tb_supplier s ON s.id=d.supplier_id WHERE d.id=? AND d.active=false",
            rs -> rs.next() ? rs.getBoolean(1) : null,
            id
        );
        if (supplierActive == null) return 0;
        if (!supplierActive) {
            throw new BusinessException(
                "Recupere o fornecedor deste documento antes de recuperar o documento."
            );
        }
        return restoreAudited("tb_supplier_documents", id);
    }

    private DeletedRecordPageResponseDto queryAuditedPage(
        String selectSql,
        String countSql,
        DeletedRecordType type,
        String search,
        int likeCount,
        int page,
        int size
    ) {
        Object[] filterArgs = filterArgs(search, likeCount);
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, filterArgs);
        long totalElements = total == null ? 0L : total;

        Object[] pageArgs = new Object[filterArgs.length + 2];
        System.arraycopy(filterArgs, 0, pageArgs, 0, filterArgs.length);
        pageArgs[filterArgs.length] = size;
        pageArgs[filterArgs.length + 1] = (long) page * size;

        List<DeletedRecordResponseDto> content = jdbcTemplate.query(
            selectSql + " LIMIT ? OFFSET ?",
            (rs, row) -> {
                Timestamp ts = rs.getTimestamp("updated_at");
                Instant at = ts == null ? null : ts.toInstant();
                return new DeletedRecordResponseDto(
                    rs.getLong("id"),
                    type,
                    rs.getString("title"),
                    rs.getString("subtitle"),
                    at
                );
            },
            pageArgs
        );

        return DeletedRecordPageResponseDto.of(
            content, page, size, totalElements
        );
    }

    private DeletedRecordPageResponseDto queryUsersPage(
        String search,
        int page,
        int size
    ) {
        Object[] filterArgs = filterArgs(search, 3);
        Long total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tb_users WHERE active=false AND (?='' OR LOWER(name) LIKE ? OR LOWER(username) LIKE ? OR LOWER(email) LIKE ?)",
            Long.class,
            filterArgs
        );
        long totalElements = total == null ? 0L : total;

        List<DeletedRecordResponseDto> content = jdbcTemplate.query(
            "SELECT id,name title,username subtitle FROM tb_users WHERE active=false AND (?='' OR LOWER(name) LIKE ? OR LOWER(username) LIKE ? OR LOWER(email) LIKE ?) ORDER BY id DESC LIMIT ? OFFSET ?",
            (rs, row) -> new DeletedRecordResponseDto(
                rs.getLong("id"),
                DeletedRecordType.USER,
                rs.getString("title"),
                rs.getString("subtitle"),
                null
            ),
            search,
            "%" + search + "%",
            "%" + search + "%",
            "%" + search + "%",
            size,
            (long) page * size
        );

        return DeletedRecordPageResponseDto.of(
            content, page, size, totalElements
        );
    }

    private Object[] filterArgs(String search, int likeCount) {
        String like = "%" + search + "%";
        Object[] args = new Object[likeCount + 1];
        args[0] = search;
        for (int i = 1; i < args.length; i++) {
            args[i] = like;
        }
        return args;
    }

    private record DeletedRecordSnapshot(String title, String subtitle) {}
}