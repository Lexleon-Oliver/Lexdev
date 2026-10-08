package net.ddns.lexdev.systempro_api.fiscal.danfe;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.ddns.lexdev.systempro_api.domain.FiscalDocument;
import net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.repository.FiscalDocumentRepository;

class NfceDanfeServiceTest {
    private final FiscalDocumentRepository repository = mock(FiscalDocumentRepository.class);
    private final NfceDanfePdfRenderer renderer = mock(NfceDanfePdfRenderer.class);
    private final NfceDanfeService service = new NfceDanfeService(repository, renderer);

    @Test
    void deveGerarSomenteParaNfceAutorizadaComXmlChaveEProtocolo() {
        FiscalDocument d = document(FiscalDocumentStatus.AUTORIZADA);
        when(repository.findBySaleId(10L)).thenReturn(Optional.of(d));
        when(renderer.render(d)).thenReturn(new byte[]{1,2,3});
        assertArrayEquals(new byte[]{1,2,3}, service.generate(10L));
        verify(renderer).render(d);
    }

    @Test
    void naoDeveGerarParaDocumentoPendente() {
        FiscalDocument d = document(FiscalDocumentStatus.PENDENTE_CONSULTA);
        when(repository.findBySaleId(10L)).thenReturn(Optional.of(d));
        assertThrows(BusinessException.class, () -> service.generate(10L));
        verifyNoInteractions(renderer);
    }

    private static FiscalDocument document(FiscalDocumentStatus status) {
        FiscalDocument d = new FiscalDocument();
        d.setStatus(status);
        d.setXml("<xml/>");
        d.setAccessKey("31261012345678000190650010000001231123456780");
        d.setProtocol("131260000000001");
        return d;
    }
}
