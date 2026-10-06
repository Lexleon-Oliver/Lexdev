package net.ddns.lexdev.systempro_api.fiscal;

import net.ddns.lexdev.systempro_api.domain.FiscalDocument;
import net.ddns.lexdev.systempro_api.domain.FiscalEstablishment;
import net.ddns.lexdev.systempro_api.domain.Sale;

public interface SefazNfceGateway {
    NfceIssueResult authorize(FiscalEstablishment establishment, Sale sale, FiscalDocument document);
    NfceIssueResult consult(FiscalEstablishment establishment, FiscalDocument document);
    NfceIssueResult cancel(FiscalEstablishment establishment, FiscalDocument document, String justification);
    NfceIssueResult status(FiscalEstablishment establishment);
}
