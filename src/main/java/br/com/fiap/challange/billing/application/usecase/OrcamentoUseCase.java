package br.com.fiap.challange.billing.application.usecase;

import br.com.fiap.challange.billing.domain.exception.AcessoNegadoException;
import br.com.fiap.challange.billing.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.challange.billing.domain.exception.RegraDeNegocioException;
import br.com.fiap.challange.billing.domain.model.Orcamento;
import br.com.fiap.challange.billing.domain.model.Pagamento;
import br.com.fiap.challange.billing.domain.model.StatusOrcamento;
import br.com.fiap.challange.billing.domain.port.in.OrcamentoInputPort;
import br.com.fiap.challange.billing.domain.port.in.command.DecisaoOrcamentoCommand;
import br.com.fiap.challange.billing.domain.port.in.command.GerarOrcamentoCommand;
import br.com.fiap.challange.billing.domain.port.out.EventosSagaPort;
import br.com.fiap.challange.billing.domain.port.out.GatewayPagamentoPort;
import br.com.fiap.challange.billing.domain.port.out.NotificacaoPort;
import br.com.fiap.challange.billing.domain.port.out.OrcamentoRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static br.com.fiap.challange.billing.domain.model.TiposMensagem.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrcamentoUseCase implements OrcamentoInputPort {

    private final OrcamentoRepositoryPort repository;
    private final GatewayPagamentoPort gatewayPagamento;
    private final EventosSagaPort eventos;
    private final NotificacaoPort notificacao;

    @Override
    public void gerar(GerarOrcamentoCommand command) {
        Optional<Orcamento> existente = repository.buscarPorSagaId(command.sagaId());
        if (existente.isPresent()) {
            // Comando repetido (reentrega do Pub/Sub): só reenvia a resposta.
            log.info("Orçamento da saga {} já existia, reenviando ORCAMENTO_GERADO", command.sagaId());
            publicarGerado(existente.get());
            return;
        }

        Orcamento orcamento;
        try {
            orcamento = Orcamento.novo(command.ordemServicoId(), command.numeroOS(), command.sagaId(),
                    command.cliente(), command.itens());
        } catch (RegraDeNegocioException e) {
            log.warn("Não foi possível gerar o orçamento da OS {}: {}", command.numeroOS(), e.getMessage());
            eventos.publicar(ORCAMENTO_FALHOU, command.sagaId(), command.ordemServicoId(),
                    Map.of("motivo", e.getMessage()));
            return;
        }

        Orcamento salvo = repository.salvar(orcamento);
        log.info("Orçamento {} gerado para a OS {} valor={}", salvo.getId(), salvo.getNumeroOS(), salvo.getValorTotal());
        notificacao.orcamentoDisponivel(salvo);
        publicarGerado(salvo);
    }

    @Override
    public Orcamento buscar(String id, String documentoCliente) {
        Orcamento orcamento = carregar(id);
        validarDono(orcamento, documentoCliente);
        return orcamento;
    }

    @Override
    public List<Orcamento> listarPorOrdemServico(Long ordemServicoId) {
        return repository.buscarPorOrdemServico(ordemServicoId);
    }

    @Override
    public Orcamento decidir(DecisaoOrcamentoCommand command) {
        Orcamento orcamento = carregar(command.orcamentoId());
        validarDono(orcamento, command.documentoCliente());
        if (orcamento.getStatus() != StatusOrcamento.AGUARDANDO_APROVACAO) {
            throw new RegraDeNegocioException("Orçamento " + orcamento.getId() + " já foi decidido (status "
                    + orcamento.getStatus() + ")");
        }

        if (command.aprovado()) {
            Pagamento cobranca = gatewayPagamento.criarCobranca(orcamento);
            orcamento.aprovar(cobranca, command.observacao());
            Orcamento salvo = repository.salvar(orcamento);
            log.info("Orçamento {} aprovado, cobrança {} criada", salvo.getId(), cobranca.getPreferenciaId());
            Map<String, Object> dados = new HashMap<>();
            dados.put("orcamentoId", salvo.getId());
            dados.put("linkPagamento", cobranca.getLinkPagamento());
            eventos.publicar(ORCAMENTO_APROVADO, salvo.getSagaId(), salvo.getOrdemServicoId(), dados);
            return salvo;
        }

        orcamento.rejeitar(command.observacao());
        Orcamento salvo = repository.salvar(orcamento);
        log.info("Orçamento {} rejeitado", salvo.getId());
        Map<String, Object> dados = new HashMap<>();
        dados.put("orcamentoId", salvo.getId());
        dados.put("motivo", command.observacao() == null ? "Rejeitado pelo cliente" : command.observacao());
        eventos.publicar(ORCAMENTO_REJEITADO, salvo.getSagaId(), salvo.getOrdemServicoId(), dados);
        return salvo;
    }

    @Override
    public void cancelarPorSaga(String sagaId, Long ordemServicoId, String motivo) {
        Optional<Orcamento> encontrado = repository.buscarPorSagaId(sagaId);
        if (encontrado.isEmpty()) {
            log.warn("Pedido de cancelamento para saga {} sem orçamento, nada a desfazer", sagaId);
            eventos.publicar(ORCAMENTO_CANCELADO, sagaId, ordemServicoId, Map.of("motivo", motivo));
            return;
        }
        Orcamento orcamento = encontrado.get();
        if (orcamento.getStatus() != StatusOrcamento.CANCELADO) {
            orcamento.cancelar(motivo);
            repository.salvar(orcamento);
            log.info("Orçamento {} cancelado pela saga {}: {}", orcamento.getId(), sagaId, motivo);
        }
        eventos.publicar(ORCAMENTO_CANCELADO, sagaId, ordemServicoId,
                Map.of("orcamentoId", orcamento.getId(), "motivo", motivo));
    }

    private void publicarGerado(Orcamento orcamento) {
        Map<String, Object> dados = new HashMap<>();
        dados.put("orcamentoId", orcamento.getId());
        dados.put("valorTotal", orcamento.getValorTotal());
        eventos.publicar(ORCAMENTO_GERADO, orcamento.getSagaId(), orcamento.getOrdemServicoId(), dados);
    }

    private Orcamento carregar(String id) {
        return repository.buscarPorId(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Orçamento não encontrado: " + id));
    }

    private void validarDono(Orcamento orcamento, String documentoCliente) {
        if (documentoCliente != null && !documentoCliente.equals(orcamento.getCliente().documento())) {
            throw new AcessoNegadoException("Este orçamento pertence a outro cliente");
        }
    }
}
