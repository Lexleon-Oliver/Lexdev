package net.ddns.lexdev.systempro_api.exception;

/**
 * Indica que a transmissão fiscal entrou na fronteira de comunicação externa
 * e terminou sem um resultado fiscal confiável. Nessa situação, a NFC-e não
 * deve ser retransmitida cegamente; o estado precisa ser confirmado por consulta.
 */
public class FiscalCommunicationException extends FiscalIntegrationException {

    public FiscalCommunicationException(String message) {
        super(message);
    }

    public FiscalCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
