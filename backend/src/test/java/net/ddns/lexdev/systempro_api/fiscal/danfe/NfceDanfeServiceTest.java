package net.ddns.lexdev.systempro_api.fiscal.danfe;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.domain.FiscalDocument;
import net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus;
import net.ddns.lexdev.systempro_api.enums.FiscalEmissionType;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.repository.FiscalDocumentRepository;

class NfceDanfeServiceTest {
    private final FiscalDocumentRepository repository = mock(FiscalDocumentRepository.class);
    private final NfceDanfePdfRenderer renderer = mock(NfceDanfePdfRenderer.class);
    private final NfceDanfeService service = new NfceDanfeService(repository, renderer);

    @Test
    void deveGerarParaNfceAutorizadaComXmlChaveEProtocolo() {
        FiscalDocument d = document(FiscalDocumentStatus.AUTORIZADA);
        d.setProtocol("131260000000001");
        when(repository.findBySaleId(10L)).thenReturn(Optional.of(d));
        when(renderer.render(d)).thenReturn(new byte[]{1,2,3});
        assertArrayEquals(new byte[]{1,2,3}, service.generate(10L));
        verify(renderer).render(d);
    }

    @Test
    void deveGerarParaContingenciaOfflineSemProtocolo() {
        FiscalDocument d = document(FiscalDocumentStatus.CONTINGENCIA);
        d.setEmissionType(FiscalEmissionType.CONTINGENCIA_OFFLINE);
        d.setContingencyAt(Instant.parse("2026-10-08T15:00:00Z"));
        d.setContingencyJustification("Indisponibilidade de comunicação com a SEFAZ.");
        when(repository.findBySaleId(10L)).thenReturn(Optional.of(d));
        when(renderer.render(d)).thenReturn(new byte[]{4,5,6});

        assertArrayEquals(new byte[]{4,5,6}, service.generate(10L));
        verify(renderer).render(d);
    }

    @Test
    void naoDeveGerarParaDocumentoPendenteConsulta() {
        FiscalDocument d = document(FiscalDocumentStatus.PENDENTE_CONSULTA);
        when(repository.findBySaleId(10L)).thenReturn(Optional.of(d));
        assertThrows(BusinessException.class, () -> service.generate(10L));
        verifyNoInteractions(renderer);
    }

    @Test
    void contingenciaOfflineExigeDadosPersistidosDaContingencia() {
        FiscalDocument d = document(FiscalDocumentStatus.CONTINGENCIA);
        d.setEmissionType(FiscalEmissionType.CONTINGENCIA_OFFLINE);
        when(repository.findBySaleId(10L)).thenReturn(Optional.of(d));

        assertThrows(BusinessException.class, () -> service.generate(10L));
        verifyNoInteractions(renderer);
    }

    private static FiscalDocument document(FiscalDocumentStatus status) {
        FiscalDocument d = new FiscalDocument();
        d.setStatus(status);
        d.setXml("<xml/>");
        d.setAccessKey("31261012345678000190650010000001231123456780");
        return d;
    }
}