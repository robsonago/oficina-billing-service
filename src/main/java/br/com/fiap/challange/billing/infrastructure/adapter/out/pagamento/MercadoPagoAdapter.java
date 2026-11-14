package br.com.fiap.challange.billing.infrastructure.adapter.out.pagamento;

import br.com.fiap.challange.billing.domain.exception.FalhaGatewayPagamentoException;
import br.com.fiap.challange.billing.domain.model.ConsultaPagamento;
import br.com.fiap.challange.billing.domain.model.ConsultaPagamento.Situacao;
import br.com.fiap.challange.billing.domain.model.ItemOrcamento;
import br.com.fiap.challange.billing.domain.model.Orcamento;
import br.com.fiap.challange.billing.domain.model.Pagamento;
import br.com.fiap.challange.billing.domain.model.StatusPagamento;
import br.com.fiap.challange.billing.domain.port.out.GatewayPagamentoPort;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Integração com o Mercado Pago (Checkout Pro).
 * <ul>
 *   <li>Aprovação do orçamento: cria uma preferência com os itens e devolve o link de pagamento.</li>
 *   <li>Webhook: consulta o pagamento em /v1/payments/{id}; o external_reference é o id do orçamento.</li>
 *   <li>Compensação: estorno total em /v1/payments/{id}/refunds.</li>
 * </ul>
 * Documentação: https://www.mercadopago.com.br/developers/pt/docs/checkout-pro/landing
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "pagamento.provider", havingValue = "mercadopago")
public class MercadoPagoAdapter implements GatewayPagamentoPort {

    private final RestClient restClient;
    private final MercadoPagoProperties properties;

    @Autowired
    public MercadoPagoAdapter(MercadoPagoProperties properties, RestClient.Builder builder) {
        this(properties, configurar(builder.clone(), properties).build());
    }

    MercadoPagoAdapter(MercadoPagoProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    static RestClient.Builder configurar(RestClient.Builder builder, MercadoPagoProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(properties.getTimeoutSegundos()));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getTimeoutSegundos()));
        return builder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getAccessToken());
    }

    @Override
    public Pagamento criarCobranca(Orcamento orcamento) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("external_reference", orcamento.getId());
        corpo.put("items", orcamento.getItens().stream().map(this::item).toList());
        corpo.put("statement_descriptor", "OFICINA");
        corpo.put("metadata", Map.of("ordem_servico", orcamento.getNumeroOS(), "saga_id", orcamento.getSagaId()));
        if (orcamento.getCliente() != null && orcamento.getCliente().email() != null) {
            corpo.put("payer", Map.of("email", orcamento.getCliente().email()));
        }
        if (properties.getNotificationUrl() != null && !properties.getNotificationUrl().isBlank()) {
            corpo.put("notification_url", properties.getNotificationUrl());
        }

        try {
            JsonNode resposta = restClient.post()
                    .uri("/checkout/preferences")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Idempotency-Key", "preferencia-" + orcamento.getId())
                    .body(corpo)
                    .retrieve()
                    .body(JsonNode.class);
            if (resposta == null) {
                throw new FalhaGatewayPagamentoException("Mercado Pago respondeu sem corpo ao criar a preferência");
            }
            String link = properties.isSandbox()
                    ? resposta.path("sandbox_init_point").asText()
                    : resposta.path("init_point").asText();
            log.info("Preferência {} criada no Mercado Pago para o orçamento {}", resposta.path("id").asText(), orcamento.getId());
            return Pagamento.builder()
                    .preferenciaId(resposta.path("id").asText())
                    .linkPagamento(link)
                    .valor(orcamento.getValorTotal())
                    .status(StatusPagamento.PENDENTE)
                    .criadoEm(Instant.now())
                    .build();
        } catch (RestClientException e) {
            throw new FalhaGatewayPagamentoException("Falha ao criar cobrança no Mercado Pago: " + e.getMessage(), e);
        }
    }

    @Override
    public ConsultaPagamento consultar(String pagamentoId) {
        try {
            JsonNode pagamento = restClient.get()
                    .uri("/v1/payments/{id}", pagamentoId)
                    .retrieve()
                    .body(JsonNode.class);
            if (pagamento == null) {
                throw new FalhaGatewayPagamentoException("Pagamento " + pagamentoId + " não encontrado no Mercado Pago");
            }
            return new ConsultaPagamento(
                    pagamento.path("id").asText(),
                    pagamento.path("external_reference").asText(),
                    traduzir(pagamento.path("status").asText()),
                    pagamento.path("status_detail").asText(null));
        } catch (RestClientException e) {
            throw new FalhaGatewayPagamentoException("Falha ao consultar pagamento " + pagamentoId + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void estornar(String pagamentoId) {
        try {
            restClient.post()
                    .uri("/v1/payments/{id}/refunds", pagamentoId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Idempotency-Key", "estorno-" + pagamentoId)
                    .body(Map.of())
                    .retrieve()
                    .toBodilessEntity();
            log.info("Estorno do pagamento {} solicitado ao Mercado Pago", pagamentoId);
        } catch (RestClientException e) {
            throw new FalhaGatewayPagamentoException("Falha ao estornar pagamento " + pagamentoId + ": " + e.getMessage(), e);
        }
    }

    static Situacao traduzir(String status) {
        return switch (status) {
            case "approved" -> Situacao.APROVADO;
            case "rejected", "cancelled" -> Situacao.RECUSADO;
            case "refunded", "charged_back" -> Situacao.ESTORNADO;
            default -> Situacao.PENDENTE; // pending, in_process, authorized, in_mediation
        };
    }

    private Map<String, Object> item(ItemOrcamento item) {
        Map<String, Object> mapa = new HashMap<>();
        mapa.put("id", item.tipo().name() + "-" + (item.referenciaId() == null ? UUID.randomUUID() : item.referenciaId()));
        mapa.put("title", item.descricao());
        mapa.put("quantity", item.quantidade());
        mapa.put("unit_price", item.precoUnitario());
        mapa.put("currency_id", "BRL");
        mapa.put("category_id", "services");
        return mapa;
    }
}
