package net.ddns.lexdev.systempro_api.repository.projection;

public interface ClientSummaryProjection {
    Long getId();
    Long getPersonId();
    String getTipoPessoa();
    String getName();
    String getCpfCnpj();
    String getEmail();
    String getPhone();
    Boolean getActive();
}
