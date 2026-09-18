package net.ddns.lexdev.systempro_api.dto;
public record PersonRequestDto(
    String cpfCnpj,
    String tipoPessoa,
    String name,
    String nomeFantasia,
    String rgIe,
    String email,
    String phone,
    String cep,
    String logradouro,
    String numero,
    String complemento,
    String bairro,
    String cidade,
    String uf
) {
    /**
     * Retorna o CPF/CNPJ contendo apenas números.
     */
    public String cleanCpfCnpj() {
        return cpfCnpj != null ? cpfCnpj.replaceAll("\\D", "") : null;
    }
}