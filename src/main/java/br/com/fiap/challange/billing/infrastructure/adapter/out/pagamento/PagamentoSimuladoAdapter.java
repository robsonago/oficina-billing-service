package br.com.fiap.challange.billing.infrastructure.adapter.out.pagamento;

import br.com.fiap.challange.billing.domain.exception.FalhaGatewayPagamentoException;
import br.com.fiap.challange.billing.domain.model.ConsultaPagamento;
import br.com.fiap.challange.billing.domain.model.ConsultaPagamento.Situacao;
import br.com.fiap.challange.billing.domain.model.Orcamento;
import br.com.fiap.challange.billing.domain.model.Pagamento;
import br.com.fiap.challange.billing.domain.model.StatusPagamento;
import br.com.fiap.challange.billing.domain.port.out.GatewayPagamentoPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gateway "de mentira" para rodar local e nos testes sem credencial do
 * Mercado Pago. O pagamento é disparado pelo endpoint
 * POST /api/pagamentos/simulacao/{orcamentoId}, que faz o papel do webhook.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "pagamento.provider", havingValue = "simulado", matchIfMissing = true)
public class PagamentoSimuladoAdapter implements GatewayPagamentoPort {

    private final String linkBase;
    private final Map<String, ConsultaPagamento> pagamentos = new ConcurrentHashMap<>();

    public PagamentoSimuladoAdapter(@Value("${pagamento.simulado.link-base:http://localhost:8081/api/pagamentos/simulacao}") String linkBase) {
        this.linkBase = linkBase;
    }

    @Override
    public Pagamento criarCobranca(Orcamento orcamento) {
        String preferencia = "SIM-" + UUID.randomUUID();
        log.info("Cobrança simulada {} criada para o orçamento {}", preferencia, orcamento.getId());
        return Pagamento.builder()
                .preferenciaId(preferencia)
                .linkPagamento(linkBase + "/" + orcamento.getId())
                .valor(orcamento.getValorTotal())
                .status(StatusPagamento.PENDENTE)
                .criadoEm(Instant.now())
                .build();
    }

    /** Registra um pagamento como se o cliente tivesse pago (ou tido o cartão recusado). */
    public String registrarPagamento(String orcamentoId, boolean aprovado) {
        String id = String.valueOf(System.nanoTime());
        pagamentos.put(id, new ConsultaPagamento(id, orcamentoId,
                aprovado ? Situacao.APROVADO : Situacao.RECUSADO,
                aprovado ? "accredited" : "cc_rejected_insufficient_amount"));
        return id;
    }

    @Override
    public ConsultaPagamento consultar(String pagamentoId) {
        ConsultaPagamento consulta = pagamentos.get(pagamentoId);
        if (consulta == null) {
            throw new FalhaGatewayPagamentoException("Pagamento simulado " + pagamentoId + " não existe");
        }
        return consulta;
    }

    @Override
    public void estornar(String pagamentoId) {
        log.info("Estorno simulado do pagamento {}", pagamentoId);
        pagamentos.computeIfPresent(pagamentoId, (id, c) ->
                new ConsultaPagamento(id, c.orcamentoId(), Situacao.ESTORNADO, "refunded"));
    }
}
