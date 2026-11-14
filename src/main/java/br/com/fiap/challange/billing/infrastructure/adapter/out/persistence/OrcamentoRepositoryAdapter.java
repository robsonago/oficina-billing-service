package br.com.fiap.challange.billing.infrastructure.adapter.out.persistence;

import br.com.fiap.challange.billing.domain.model.Orcamento;
import br.com.fiap.challange.billing.domain.port.out.OrcamentoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class OrcamentoRepositoryAdapter implements OrcamentoRepositoryPort {

    private final OrcamentoMongoRepository mongo;

    @Override
    public Orcamento salvar(Orcamento orcamento) {
        // Mantém a versão atual do documento para o controle otimista do
        // @Version: duas réplicas mexendo no mesmo orçamento não se sobrescrevem.
        Long versao = orcamento.getId() == null ? null
                : mongo.findById(orcamento.getId()).map(OrcamentoDocument::getVersao).orElse(null);
        return OrcamentoMapper.toDomain(mongo.save(OrcamentoMapper.toDocument(orcamento, versao)));
    }

    @Override
    public Optional<Orcamento> buscarPorId(String id) {
        return mongo.findById(id).map(OrcamentoMapper::toDomain);
    }

    @Override
    public Optional<Orcamento> buscarPorSagaId(String sagaId) {
        return mongo.findBySagaId(sagaId).map(OrcamentoMapper::toDomain);
    }

    @Override
    public List<Orcamento> buscarPorOrdemServico(Long ordemServicoId) {
        return mongo.findByOrdemServicoIdOrderByCriadoEmDesc(ordemServicoId).stream()
                .map(OrcamentoMapper::toDomain).toList();
    }
}
