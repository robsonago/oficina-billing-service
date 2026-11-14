package br.com.fiap.challange.billing.domain.port.out;

import br.com.fiap.challange.billing.domain.model.Orcamento;

import java.util.List;
import java.util.Optional;

public interface OrcamentoRepositoryPort {
    Orcamento salvar(Orcamento orcamento);
    Optional<Orcamento> buscarPorId(String id);
    Optional<Orcamento> buscarPorSagaId(String sagaId);
    List<Orcamento> buscarPorOrdemServico(Long ordemServicoId);
}
