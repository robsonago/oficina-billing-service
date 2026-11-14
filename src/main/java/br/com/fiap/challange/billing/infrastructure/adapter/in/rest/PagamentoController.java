package br.com.fiap.challange.billing.infrastructure.adapter.in.rest;

import br.com.fiap.challange.billing.domain.port.in.PagamentoInputPort;
import br.com.fiap.challange.billing.infrastructure.adapter.in.rest.dto.OrcamentoResponse;
import br.com.fiap.challange.billing.infrastructure.adapter.in.rest.dto.SimulacaoPagamentoRequest;
import br.com.fiap.challange.billing.infrastructure.adapter.out.pagamento.PagamentoSimuladoAdapter;
import br.com.fiap.challange.billing.domain.port.in.OrcamentoInputPort;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/pagamentos")
@Tag(name = "Pagamentos", description = "Webhook do Mercado Pago e simulação de pagamento para testes")
public class PagamentoController {

    private final PagamentoInputPort pagamentos;
    private final OrcamentoInputPort orcamentos;
    private final AssinaturaWebhookValidator assinatura;
    private final ObjectProvider<PagamentoSimuladoAdapter> simulador;

    public PagamentoController(PagamentoInputPort pagamentos, OrcamentoInputPort orcamentos,
                               AssinaturaWebhookValidator assinatura,
                               ObjectProvider<PagamentoSimuladoAdapter> simulador) {
        this.pagamentos = pagamentos;
        this.orcamentos = orcamentos;
        this.assinatura = assinatura;
        this.simulador = simulador;
    }

    /**
     * Notificação do Mercado Pago. Aceita tanto o formato novo (corpo JSON
     * com type/data.id) quanto o antigo (query string topic/id). Só eventos
     * de pagamento interessam; o resto recebe 200 para o MP parar de reenviar.
     */
    @PostMapping("/webhook")
    @Operation(summary = "Webhook de notificações do Mercado Pago (público, validado pela assinatura x-signature)")
    public ResponseEntity<Void> webhook(@RequestHeader(value = "x-signature", required = false) String xSignature,
                                        @RequestHeader(value = "x-request-id", required = false) String xRequestId,
                                        @RequestParam(value = "data.id", required = false) String dataIdQuery,
                                        @RequestParam(value = "type", required = false) String typeQuery,
                                        @RequestParam(value = "topic", required = false) String topicQuery,
                                        @RequestParam(value = "id", required = false) String idQuery,
                                        @RequestBody(required = false) JsonNode corpo) {
        String tipo = primeiroPreenchido(corpo == null ? null : corpo.path("type").asText(null), typeQuery, topicQuery);
        String pagamentoId = primeiroPreenchido(dataIdQuery,
                corpo == null ? null : corpo.path("data").path("id").asText(null), idQuery);

        if (!assinatura.valida(xSignature, xRequestId, pagamentoId)) {
            log.warn("Webhook recusado: assinatura inválida para o pagamento {}", pagamentoId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!"payment".equals(tipo) || pagamentoId == null) {
            log.info("Notificação do Mercado Pago ignorada tipo={} id={}", tipo, pagamentoId);
            return ResponseEntity.ok().build();
        }
        log.info("Notificação de pagamento {} recebida", pagamentoId);
        pagamentos.processarNotificacao(pagamentoId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/simulacao/{orcamentoId}")
    @Operation(summary = "Simula o cliente pagando (ou tendo o cartão recusado). Só existe com pagamento.provider=simulado")
    public ResponseEntity<Object> simular(@PathVariable String orcamentoId,
                                         @Valid @RequestBody SimulacaoPagamentoRequest request) {
        PagamentoSimuladoAdapter adapter = simulador.getIfAvailable();
        if (adapter == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("mensagem", "Simulação desligada: este ambiente usa o Mercado Pago de verdade"));
        }
        String pagamentoId = adapter.registrarPagamento(orcamentoId, request.aprovado());
        pagamentos.processarNotificacao(pagamentoId);
        return ResponseEntity.ok(OrcamentoResponse.from(orcamentos.buscar(orcamentoId, null)));
    }

    private static String primeiroPreenchido(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
