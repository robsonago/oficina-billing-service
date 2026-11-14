package br.com.fiap.challange.billing.application.usecase;

import br.com.fiap.challange.billing.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.challange.billing.domain.model.ConsultaPagamento;
import br.com.fiap.challange.billing.domain.model.Orcamento;
import br.com.fiap.challange.billing.domain.model.StatusOrcamento;
import br.com.fiap.challange.billing.domain.port.in.OrcamentoInputPort;
import br.com.fiap.challange.billing.domain.port.in.PagamentoInputPort;
import br.com.fiap.challange.billing.domain.port.out.EventosSagaPort;
import br.com.fiap.challange.billing.domain.port.out.GatewayPagamentoPort;
import br.com.fiap.challange.billing.domain.port.out.OrcamentoRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static br.com.fiap.challange.billing.domain.model.TiposMensagem.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PagamentoUseCase implements PagamentoInputPort {

    private final OrcamentoRepositoryPort repository;
    private final GatewayPagamentoPort gatewayPagamento;
    private final EventosSagaPort eventos;
    private final OrcamentoInputPort orcamentos;

    /**
     * Chamado a partir do webhook do Mercado Pago. A notificação só traz o id
     * do pagamento; a situação real é sempre consultada na API, nunca
     * confiamos no corpo da notificação.
     */
    @Override
    public void processarNotificacao(String pagamentoId) {
        ConsultaPagamento consulta = gatewayPagamento.consultar(pagamentoId);
        Orcamento orcamento = repository.buscarPorId(consulta.orcamentoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Pagamento " + pagamentoId + " aponta para orçamento inexistente: " + consulta.orcamentoId()));

        if (orcamento.getStatus() != StatusOrcamento.APROVADO) {
            log.info("Notificação do pagamento {} ignorada: orçamento {} já está {}", pagamentoId,
                    orcamento.getId(), orcamento.getStatus());
            return;
        }

        switch (consulta.situacao()) {
            case APROVADO -> {
                orcamento.confirmarPagamento(pagamentoId);
                repository.salvar(orcamento);
                log.info("Pagamento {} aprovado para o orçamento {}", pagamentoId, orcamento.getId());
                Map<String, Object> dados = new HashMap<>();
                dados.put("orcamentoId", orcamento.getId());
                dados.put("pagamentoId", pagamentoId);
                dados.put("valor", orcamento.getValorTotal());
                eventos.publicar(PAGAMENTO_APROVADO, orcamento.getSagaId(), orcamento.getOrdemServicoId(), dados);
            }
            case RECUSADO -> {
                String motivo = consulta.detalhe() == null ? "recusado pelo emissor" : consulta.detalhe();
                orcamento.recusarPagamento(pagamentoId, motivo);
                repository.salvar(orcamento);
                log.info("Pagamento {} recusado para o orçamento {}: {}", pagamentoId, orcamento.getId(), motivo);
                Map<String, Object> dados = new HashMap<>();
                dados.put("orcamentoId", orcamento.getId());
                dados.put("pagamentoId", pagamentoId);
                dados.put("motivo", motivo);
                eventos.publicar(PAGAMENTO_RECUSADO, orcamento.getSagaId(), orcamento.getOrdemServicoId(), dados);
            }
            default -> log.info("Pagamento {} ainda em processamento ({}), aguardando nova notificação",
                    pagamentoId, consulta.situacao());
        }
    }

    @Override
    public void estornarPorSaga(String sagaId, Long ordemServicoId, String motivo) {
        Optional<Orcamento> encontrado = repository.buscarPorSagaId(sagaId);
        if (encontrado.isEmpty() || (!encontrado.get().isPago()
                && encontrado.get().getStatus() != StatusOrcamento.ESTORNADO)) {
            // Nada foi cobrado ainda: compensar é só cancelar o orçamento.
            orcamentos.cancelarPorSaga(sagaId, ordemServicoId, motivo);
            return;
        }

        Orcamento orcamento = encontrado.get();
        if (orcamento.isPago()) {
            gatewayPagamento.estornar(orcamento.getPagamento().getId());
            orcamento.registrarEstorno(motivo);
            repository.salvar(orcamento);
            log.info("Pagamento {} estornado (saga {}): {}", orcamento.getPagamento().getId(), sagaId, motivo);
        }
        Map<String, Object> dados = new HashMap<>();
        dados.put("orcamentoId", orcamento.getId());
        dados.put("pagamentoId", orcamento.getPagamento().getId());
        dados.put("motivo", motivo);
        eventos.publicar(PAGAMENTO_ESTORNADO, sagaId, ordemServicoId, dados);
    }
}
