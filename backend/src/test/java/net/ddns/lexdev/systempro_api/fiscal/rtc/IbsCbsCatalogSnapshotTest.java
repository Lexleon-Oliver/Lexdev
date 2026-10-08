package net.ddns.lexdev.systempro_api.fiscal.rtc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class IbsCbsCatalogSnapshotTest {

    private static final String MIGRATION =
        "/db/migration/V17__carregar_catalogo_oficial_ibs_cbs_it_2025_002_v1_70.sql";

    private static final Pattern C_CLASS_TRIB_TUPLE =
        Pattern.compile("(?m)^\\s*\\('[0-9]{6}',");

    @Test
    void snapshotOficialDeveSerIntegralVersionadoEAuditavel() throws IOException {
        String sql;
        try (var in = getClass().getResourceAsStream(MIGRATION)) {
            assertTrue(in != null, "Migration V17 do catálogo RTC não encontrada.");
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(sql.contains("IT 2025.002 v1.70"));
        assertTrue(sql.contains("esperado 173 registros"));
        assertTrue(sql.contains("ON CONFLICT (c_class_trib) DO UPDATE"));
        assertTrue(sql.contains("'000001'"));

        long tuples = C_CLASS_TRIB_TUPLE.matcher(sql).results().count();
        assertEquals(173, tuples);

        assertFalse(sql.contains("INSERT INTO tb_ibs_cbs_tax_classification")
            && sql.contains("DELETE FROM tb_ibs_cbs_tax_classification"));
    }
}