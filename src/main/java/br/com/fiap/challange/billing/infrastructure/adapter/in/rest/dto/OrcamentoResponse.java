package br.com.fiap.challange.billing.infrastructure.adapter.in.rest.dto;

import br.com.fiap.challange.billing.domain.model.ClienteOrcamento;
import br.com.fiap.challange.billing.domain.model.HistoricoOrcamento;
import br.com.fiap.challange.billing.domain.model.ItemOrcamento;
import br.com.fiap.challange.billing.domain.model.Orcamento;
import br.com.fiap.challange.billing.domain.model.Pagamento;
import br.com.fiap.challange.billing.domain.model.StatusOrcamento;
import br.com.fiap.challange.billing.domain.model.StatusPagamento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrcamentoResponse(
        String id,
        Long ordemServicoId,
        String numeroOS,
        String sagaId,
        ClienteOrcamento cliente,
        List<ItemResponse> itens,
        BigDecimal valorTotal,
        StatusOrcamento status,
        PagamentoResponse pagamento,
        String observacao,
        List<HistoricoOrcamento> historico,
        Instant criadoEm,
        Instant atualizadoEm) {

    public record ItemResponse(String tipo, Long referenciaId, String descricao, int quantidade,
                               BigDecimal precoUnitario, BigDecimal subtotal) {
        static ItemResponse from(ItemOrcamento i) {
            return new ItemResponse(i.tipo().name(), i.referenciaId(), i.descricao(), i.quantidade(),
                    i.precoUnitario(), i.subtotal());
        }
    }

    public record PagamentoResponse(String id, String preferenciaId, String linkPagamento, BigDecimal valor,
                                    StatusPagamento status, Instant atualizadoEm) {
        static PagamentoResponse from(Pagamento p) {
            if (p == null) {
                return null;
            }
            return new PagamentoResponse(p.getId(), p.getPreferenciaId(), p.getLinkPagamento(), p.getValor(),
                    p.getStatus(), p.getAtualizadoEm() != null ? p.getAtualizadoEm() : p.getCriadoEm());
        }
    }

    public static OrcamentoResponse from(Orcamento o) {
        return new OrcamentoResponse(o.getId(), o.getOrdemServicoId(), o.getNumeroOS(), o.getSagaId(),
                o.getCliente(), o.getItens().stream().map(ItemResponse::from).toList(), o.getValorTotal(),
                o.getStatus(), PagamentoResponse.from(o.getPagamento()), o.getObservacao(), o.getHistorico(),
                o.getCriadoEm(), o.getAtualizadoEm());
    }
}
