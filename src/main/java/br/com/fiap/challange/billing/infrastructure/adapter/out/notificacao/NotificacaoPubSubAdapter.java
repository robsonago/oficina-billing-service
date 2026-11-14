package br.com.fiap.challange.billing.infrastructure.adapter.out.notificacao;

import br.com.fiap.challange.billing.domain.model.Orcamento;
import br.com.fiap.challange.billing.domain.port.out.NotificacaoPort;
import br.com.fiap.challange.billing.infrastructure.messaging.CanalMensageria;
import br.com.fiap.challange.billing.infrastructure.messaging.Rastreamento;
import br.com.fiap.challange.billing.infrastructure.metrics.BillingMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Avisa o cliente que o orçamento está disponível. Publica no mesmo tópico
 * de notificações da Fase 3; a function de notificação (repo
 * oficina-auth-function) é quem envia o e-mail. Vai sem o envelope da saga,
 * no mesmo formato plano que a function já lê.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificacaoPubSubAdapter implements NotificacaoPort {

    static final String DESTINO = "notificacoes";

    private final CanalMensageria canal;
    private final ObjectMapper objectMapper;
    private final BillingMetrics metrics;

    @Override
    public void orcamentoDisponivel(Orcamento orcamento) {
        if (orcamento.getCliente() == null || orcamento.getCliente().email() == null
                || orcamento.getCliente().email().isBlank()) {
            log.warn("Orçamento {} sem e-mail do cliente, notificação não enviada", orcamento.getId());
            return;
        }
        ObjectNode evento = objectMapper.createObjectNode();
        evento.put("tipo", "ORCAMENTO_DISPONIVEL");
        evento.put("destinatario", orcamento.getCliente().email());
        evento.put("nomeCliente", orcamento.getCliente().nome());
        evento.put("numeroOS", orcamento.getNumeroOS());
        evento.put("valorTotal", orcamento.getValorTotal().toPlainString());
        try {
            canal.enviarSemEnvelope(DESTINO, "ORCAMENTO_DISPONIVEL", evento,
                    Rastreamento.cabecalhosAtuais(orcamento.getSagaId()));
        } catch (RuntimeException e) {
            // Notificação não pode derrubar a saga: o orçamento existe e o
            // cliente consegue consultá-lo pela API mesmo sem o e-mail.
            log.error("Falha ao publicar notificação do orçamento {}: {}", orcamento.getId(), e.getMessage());
            metrics.erroIntegracao("notificacao");
        }
    }
}
