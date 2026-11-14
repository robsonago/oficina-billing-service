package br.com.fiap.challange.billing.domain.model;

import br.com.fiap.challange.billing.domain.exception.RegraDeNegocioException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Orcamento {

    private String id;
    private Long ordemServicoId;
    private String numeroOS;
    private String sagaId;
    private ClienteOrcamento cliente;

    @Builder.Default
    private List<ItemOrcamento> itens = new ArrayList<>();

    @Builder.Default
    private BigDecimal valorTotal = BigDecimal.ZERO;

    private StatusOrcamento status;
    private Pagamento pagamento;
    private String observacao;

    @Builder.Default
    private List<HistoricoOrcamento> historico = new ArrayList<>();

    private Instant criadoEm;
    private Instant atualizadoEm;

    public static Orcamento novo(Long ordemServicoId, String numeroOS, String sagaId,
                                 ClienteOrcamento cliente, List<ItemOrcamento> itens) {
        if (itens == null || itens.isEmpty()) {
            throw new RegraDeNegocioException("A OS " + numeroOS + " não tem serviços nem peças para orçar");
        }
        BigDecimal total = itens.stream().map(ItemOrcamento::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            throw new RegraDeNegocioException("O orçamento da OS " + numeroOS + " ficou com valor zerado");
        }
        Orcamento orcamento = Orcamento.builder()
                .ordemServicoId(ordemServicoId)
                .numeroOS(numeroOS)
                .sagaId(sagaId)
                .cliente(cliente)
                .itens(new ArrayList<>(itens))
                .valorTotal(total)
                .status(StatusOrcamento.AGUARDANDO_APROVACAO)
                .criadoEm(Instant.now())
                .build();
        orcamento.registrar(StatusOrcamento.AGUARDANDO_APROVACAO, "Orçamento gerado");
        return orcamento;
    }

    public void aprovar(Pagamento cobranca, String observacao) {
        exigirStatus(StatusOrcamento.AGUARDANDO_APROVACAO);
        this.pagamento = cobranca;
        this.observacao = observacao;
        mudarPara(StatusOrcamento.APROVADO, "Aprovado pelo cliente, aguardando pagamento");
    }

    public void rejeitar(String observacao) {
        exigirStatus(StatusOrcamento.AGUARDANDO_APROVACAO);
        this.observacao = observacao;
        mudarPara(StatusOrcamento.REJEITADO, observacao == null ? "Rejeitado pelo cliente" : "Rejeitado: " + observacao);
    }

    public void confirmarPagamento(String pagamentoId) {
        exigirStatus(StatusOrcamento.APROVADO);
        pagamento.setId(pagamentoId);
        pagamento.setStatus(StatusPagamento.APROVADO);
        pagamento.setAtualizadoEm(Instant.now());
        mudarPara(StatusOrcamento.PAGO, "Pagamento " + pagamentoId + " aprovado");
    }

    public void recusarPagamento(String pagamentoId, String motivo) {
        exigirStatus(StatusOrcamento.APROVADO);
        pagamento.setId(pagamentoId);
        pagamento.setStatus(StatusPagamento.RECUSADO);
        pagamento.setAtualizadoEm(Instant.now());
        mudarPara(StatusOrcamento.PAGAMENTO_RECUSADO, "Pagamento recusado: " + motivo);
    }

    public void cancelar(String motivo) {
        Set<StatusOrcamento> cancelaveis = EnumSet.of(StatusOrcamento.AGUARDANDO_APROVACAO,
                StatusOrcamento.APROVADO, StatusOrcamento.PAGAMENTO_RECUSADO);
        if (!cancelaveis.contains(status)) {
            throw new RegraDeNegocioException("Orçamento " + id + " não pode ser cancelado no status " + status);
        }
        mudarPara(StatusOrcamento.CANCELADO, "Cancelado: " + motivo);
    }

    public void registrarEstorno(String motivo) {
        exigirStatus(StatusOrcamento.PAGO);
        pagamento.setStatus(StatusPagamento.ESTORNADO);
        pagamento.setAtualizadoEm(Instant.now());
        mudarPara(StatusOrcamento.ESTORNADO, "Pagamento estornado: " + motivo);
    }

    public boolean isPago() {
        return status == StatusOrcamento.PAGO;
    }

    private void exigirStatus(StatusOrcamento esperado) {
        if (status != esperado) {
            throw new RegraDeNegocioException(String.format(
                    "Orçamento %s está %s, era esperado %s", id, status, esperado));
        }
    }

    private void mudarPara(StatusOrcamento novo, String descricao) {
        this.status = novo;
        registrar(novo, descricao);
    }

    private void registrar(StatusOrcamento novo, String descricao) {
        this.atualizadoEm = Instant.now();
        this.historico.add(new HistoricoOrcamento(novo, descricao, atualizadoEm));
    }
}
