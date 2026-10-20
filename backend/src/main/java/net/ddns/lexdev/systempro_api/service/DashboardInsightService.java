package net.ddns.lexdev.systempro_api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import net.ddns.lexdev.systempro_api.ai.AiGateway;
import net.ddns.lexdev.systempro_api.config.AiProperties;
import net.ddns.lexdev.systempro_api.dto.dashboard.DashboardInsightResponseDto;
import net.ddns.lexdev.systempro_api.dto.dashboard.DashboardInsightResponseDto.Status;
import net.ddns.lexdev.systempro_api.dto.dashboard.DashboardResponseDto;

@Service
public class DashboardInsightService {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(DashboardInsightService.class);

    private static final String DISABLED_MESSAGE =
        "Os insights por IA estão desativados. "
        + "Os indicadores do dashboard continuam disponíveis normalmente.";

    private static final String UNAVAILABLE_MESSAGE =
        "Não foi possível gerar o insight por IA agora. "
        + "Os indicadores do dashboard continuam disponíveis normalmente.";

    private final DashboardService dashboardService;
    private final AiGateway aiGateway;
    private final AiProperties properties;
    private final JsonMapper objectMapper;

    public DashboardInsightService(
        DashboardService dashboardService,
        AiGateway aiGateway,
        AiProperties properties,
        JsonMapper objectMapper
    ) {
        this.dashboardService = dashboardService;
        this.aiGateway = aiGateway;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public DashboardInsightResponseDto insight() {
        DashboardResponseDto dashboard = dashboardService.dashboard();

        if (!properties.enabled()) {
            return new DashboardInsightResponseDto(
                Status.DISABLED,
                DISABLED_MESSAGE
            );
        }

        if (!isProviderSupported()) {
            return new DashboardInsightResponseDto(
                Status.UNAVAILABLE,
                UNAVAILABLE_MESSAGE
            );
        }

        try {
            String insight = aiGateway.generateInsight(
                buildPrompt(dashboard)
            );

            return new DashboardInsightResponseDto(
                Status.GENERATED,
                insight
            );

        } catch (RuntimeException ex) {
            LOGGER.warn(
                "Falha na geração de insight por IA; "
                    + "fallback local será retornado. provider={}, exception={}, reason={}",
                safe(properties.provider()),
                ex.getClass().getSimpleName(),
                safe(ex.getMessage())
            );

            return new DashboardInsightResponseDto(
                Status.UNAVAILABLE,
                UNAVAILABLE_MESSAGE
            );
        }
    }

    private String buildPrompt(DashboardResponseDto dashboard) {
        try {
            ObjectNode data = objectMapper.createObjectNode();

            if (dashboard.date() == null) {
                data.putNull("date");
            } else {
                data.put("date", dashboard.date().toString());
            }

            /*
             * Data minimization:
             * recentSales não é enviado ao provedor externo.
             */
            data.set(
                "sales",
                objectMapper.valueToTree(dashboard.sales())
            );
            data.set(
                "fiscal",
                objectMapper.valueToTree(dashboard.fiscal())
            );
            data.set(
                "payments",
                objectMapper.valueToTree(dashboard.payments())
            );
            data.set(
                "dailyPerformance",
                objectMapper.valueToTree(dashboard.dailyPerformance())
            );

            return """
                Analise os indicadores operacionais abaixo e destaque os fatos
                mais relevantes para o gestor neste momento.

                Regras obrigatórias para a resposta:
                - Responda em português do Brasil, de forma objetiva e profissional.
                - Use no máximo quatro frases curtas.
                - Na resposta final, apresente datas no formato dd/MM/yyyy.
                  Os dados de entrada podem estar em formato ISO, mas não reproduza
                  esse formato para o usuário.
                - Apresente valores monetários no padrão brasileiro, como R$ 18,00.
                - Diferencie claramente indicadores de hoje do histórico recente.
                - Não atribua a data do dashboard a eventos, pendências ou estados que
                  não possuam data própria nos dados fornecidos. A data do dashboard
                  representa apenas a data de referência da consulta.
                - Não transforme ausência ou baixo volume de vendas em diagnóstico
                  de crise, estagnação, deterioração ou problema operacional.
                - Só descreva tendência de alta, queda ou estabilidade quando houver
                  pelo menos três dias com movimentação que sustentem essa conclusão.
                  Caso contrário, apenas descreva os registros disponíveis.
                - Não invente causas, projeções ou comparações com períodos que não
                  foram fornecidos.
                - Não use palavras como urgente, crítico, grave, risco ou atenção
                  imediata sem evidência explícita nos dados fornecidos.
                - Não conte a mesma pendência fiscal duas vezes. Se todas as
                  pendências estiverem na mesma categoria, consolide a informação
                  em uma única frase.
                - Para pendências fiscais, prefira linguagem neutra, como
                  "merecem acompanhamento" ou "estão aguardando tratamento".
                - Evite simplesmente repetir todos os KPIs. Priorize o que realmente
                  acrescenta contexto para o gestor.
                - Não forneça aconselhamento jurídico, contábil ou fiscal.

                Dados do dashboard:
                """ + data;

        } catch (JacksonException ex) {
            throw new IllegalStateException(
                "Não foi possível serializar os dados do dashboard para a IA.",
                ex
            );
        }
    }

    private boolean isProviderSupported() {
        String provider = properties.provider();

        return provider != null
            && (
                "openai".equalsIgnoreCase(provider)
                || "gemini".equalsIgnoreCase(provider)
            );
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

}
