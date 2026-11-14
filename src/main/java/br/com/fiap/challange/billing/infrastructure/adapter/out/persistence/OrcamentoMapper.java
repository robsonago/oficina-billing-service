package br.com.fiap.challange.billing.infrastructure.adapter.out.persistence;

import br.com.fiap.challange.billing.domain.model.Orcamento;
import br.com.fiap.challange.billing.domain.model.Pagamento;

import java.util.ArrayList;

final class OrcamentoMapper {

    private OrcamentoMapper() {
    }

    static OrcamentoDocument toDocument(Orcamento o, Long versao) {
        return OrcamentoDocument.builder()
                .id(o.getId())
                .ordemServicoId(o.getOrdemServicoId())
                .numeroOS(o.getNumeroOS())
                .sagaId(o.getSagaId())
                .cliente(o.getCliente())
                .itens(o.getItens())
                .valorTotal(o.getValorTotal())
                .status(o.getStatus())
                .pagamento(toDocument(o.getPagamento()))
                .observacao(o.getObservacao())
                .historico(o.getHistorico())
                .criadoEm(o.getCriadoEm())
                .atualizadoEm(o.getAtualizadoEm())
                .versao(versao)
                .build();
    }

    static Orcamento toDomain(OrcamentoDocument d) {
        return Orcamento.builder()
                .id(d.getId())
                .ordemServicoId(d.getOrdemServicoId())
                .numeroOS(d.getNumeroOS())
                .sagaId(d.getSagaId())
                .cliente(d.getCliente())
                .itens(d.getItens() == null ? new ArrayList<>() : new ArrayList<>(d.getItens()))
                .valorTotal(d.getValorTotal())
                .status(d.getStatus())
                .pagamento(toDomain(d.getPagamento()))
                .observacao(d.getObservacao())
                .historico(d.getHistorico() == null ? new ArrayList<>() : new ArrayList<>(d.getHistorico()))
                .criadoEm(d.getCriadoEm())
                .atualizadoEm(d.getAtualizadoEm())
                .build();
    }

    private static PagamentoDocument toDocument(Pagamento p) {
        if (p == null) {
            return null;
        }
        return PagamentoDocument.builder().id(p.getId()).preferenciaId(p.getPreferenciaId())
                .linkPagamento(p.getLinkPagamento()).valor(p.getValor()).status(p.getStatus())
                .criadoEm(p.getCriadoEm()).atualizadoEm(p.getAtualizadoEm()).build();
    }

    private static Pagamento toDomain(PagamentoDocument p) {
        if (p == null) {
            return null;
        }
        return Pagamento.builder().id(p.getId()).preferenciaId(p.getPreferenciaId())
                .linkPagamento(p.getLinkPagamento()).valor(p.getValor()).status(p.getStatus())
                .criadoEm(p.getCriadoEm()).atualizadoEm(p.getAtualizadoEm()).build();
    }
}
