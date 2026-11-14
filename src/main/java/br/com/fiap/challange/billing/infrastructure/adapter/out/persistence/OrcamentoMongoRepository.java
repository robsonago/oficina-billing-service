package br.com.fiap.challange.billing.infrastructure.adapter.out.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

interface OrcamentoMongoRepository extends MongoRepository<OrcamentoDocument, String> {
    Optional<OrcamentoDocument> findBySagaId(String sagaId);
    List<OrcamentoDocument> findByOrdemServicoIdOrderByCriadoEmDesc(Long ordemServicoId);
}
