package br.com.fiap.challange.billing.domain.port.in;

import br.com.fiap.challange.billing.domain.model.Orcamento;
import br.com.fiap.challange.billing.domain.port.in.command.DecisaoOrcamentoCommand;
import br.com.fiap.challange.billing.domain.port.in.command.GerarOrcamentoCommand;

import java.util.List;

public interface OrcamentoInputPort {
    void gerar(GerarOrcamentoCommand command);
    Orcamento buscar(String id, String documentoCliente);
    List<Orcamento> listarPorOrdemServico(Long ordemServicoId);
    Orcamento decidir(DecisaoOrcamentoCommand command);
    void cancelarPorSaga(String sagaId, Long ordemServicoId, String motivo);
}
