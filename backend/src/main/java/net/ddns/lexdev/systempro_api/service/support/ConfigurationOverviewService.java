package net.ddns.lexdev.systempro_api.service.support;

import java.util.List;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import net.ddns.lexdev.systempro_api.dto.support.ConfigurationOverviewDto;
import net.ddns.lexdev.systempro_api.dto.support.ConfigurationOverviewDto.FeatureStatus;
import net.ddns.lexdev.systempro_api.dto.support.ConfigurationOverviewDto.ManagementArea;

/**
 * Catálogo fechado de diagnósticos não sigilosos para o suporte.
 *
 * Não busca propriedades por parâmetros de requisição e não devolve segredos.
 * Esta API não é um editor e não altera valores de Environment, banco ou arquivos.
 */
@Service
public class ConfigurationOverviewService {

    private final Environment environment;

    public ConfigurationOverviewService(Environment environment) {
        this.environment = environment;
    }

    public ConfigurationOverviewDto overview() {
        return new ConfigurationOverviewDto(
            List.of(
                feature("systempro.fiscal.enabled", "Integração fiscal", "Fiscal"),
                feature("systempro.fiscal.production-ready", "Emissão fiscal de produção", "Fiscal"),
                feature("systempro.fiscal.schema.emit-liquid-values", "Emissão de valores líquidos", "Fiscal experimental"),
                feature("systempro.fiscal.liquid-zero-tax-producer-enabled", "Produtor ZERO-TAX", "Fiscal experimental"),
                feature("systempro.ai.enabled", "Assistente de IA", "Inteligência artificial"),
                feature("systempro.backup.scheduling.enabled", "Agendamento de backups", "Backup")
            ),
            List.of(
                new ManagementArea("Empresa e estabelecimento", "CLIENTE", "/configuracoes/fiscal",
                    "Os dados do estabelecimento já possuem fluxo próprio no sistema."),
                new ManagementArea("Estoque", "CLIENTE", "/configuracoes/estoque",
                    "A antecedência mínima para venda por validade já é persistida no banco."),
                new ManagementArea("Recursos técnicos", "SUPORTE", "/suporte/configuracoes-tecnicas",
                    "Somente diagnóstico. Alterações nas flags dependem de configuração externa e reinicialização do backend."),
                new ManagementArea("Banco, armazenamento e integrações", "INSTALADOR", "Ambiente da instalação",
                    "Variáveis de ambiente e volumes devem permanecer externos à imagem e preservados nas atualizações."),
                new ManagementArea("Segredos e licenciamento", "INSTALADOR", "Ambiente protegido",
                    "Senhas, tokens, chaves privadas e informações de licença não são retornados por esta API.")
            )
        );
    }

    private FeatureStatus feature(String key, String label, String category) {
        // Lista explícita e imutável: não aceitar chave arbitrária fornecida pelo cliente HTTP.
        return new FeatureStatus(key, label, category,
            environment.getProperty(key, Boolean.class, false), true);
    }
}
