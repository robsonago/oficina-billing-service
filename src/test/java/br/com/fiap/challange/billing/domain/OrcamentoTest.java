package br.com.fiap.challange.billing.domain;

import br.com.fiap.challange.billing.Fixtures;
import br.com.fiap.challange.billing.domain.exception.RegraDeNegocioException;
import br.com.fiap.challange.billing.domain.model.ItemOrcamento;
import br.com.fiap.challange.billing.domain.model.Orcamento;
import br.com.fiap.challange.billing.domain.model.StatusOrcamento;
import br.com.fiap.challange.billing.domain.model.StatusPagamento;
import br.com.fiap.challange.billing.domain.model.TipoItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrcamentoTest {

    @Test
    void novoOrcamentoSomaOsItensEFicaAguardandoAprovacao() {
        Orcamento o = Fixtures.orcamentoAguardando();

        assertThat(o.getValorTotal()).isEqualByComparingTo("151.00");
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.AGUARDANDO_APROVACAO);
        assertThat(o.getHistorico()).hasSize(1);
    }

    @Test
    void naoGeraOrcamentoSemItens() {
        assertThatThrownBy(() -> Orcamento.novo(1L, "OS-1", "s", Fixtures.cliente(), List.of()))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("não tem serviços nem peças");
        assertThatThrownBy(() -> Orcamento.novo(1L, "OS-1", "s", Fixtures.cliente(), null))
                .isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void naoGeraOrcamentoZerado() {
        List<ItemOrcamento> gratis = List.of(new ItemOrcamento(TipoItem.SERVICO, 1L, "Cortesia", 1, BigDecimal.ZERO));
        assertThatThrownBy(() -> Orcamento.novo(1L, "OS-1", "s", Fixtures.cliente(), gratis))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("zerado");
    }

    @Test
    void aprovarGuardaACobranca() {
        Orcamento o = Fixtures.orcamentoAprovado();

        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.APROVADO);
        assertThat(o.getPagamento().getLinkPagamento()).isNotBlank();
    }

    @Test
    void rejeitarComESemObservacao() {
        Orcamento comMotivo = Fixtures.orcamentoAguardando();
        comMotivo.rejeitar("Muito caro");
        assertThat(comMotivo.getStatus()).isEqualTo(StatusOrcamento.REJEITADO);
        assertThat(comMotivo.getHistorico().getLast().descricao()).contains("Muito caro");

        Orcamento semMotivo = Fixtures.orcamentoAguardando();
        semMotivo.rejeitar(null);
        assertThat(semMotivo.getHistorico().getLast().descricao()).isEqualTo("Rejeitado pelo cliente");
    }

    @Test
    void naoAprovaDuasVezes() {
        Orcamento o = Fixtures.orcamentoAprovado();
        assertThatThrownBy(() -> o.aprovar(Fixtures.cobranca(), null))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("era esperado AGUARDANDO_APROVACAO");
    }

    @Test
    void confirmarPagamento() {
        Orcamento o = Fixtures.orcamentoPago();

        assertThat(o.isPago()).isTrue();
        assertThat(o.getPagamento().getId()).isEqualTo("pay-99");
        assertThat(o.getPagamento().getStatus()).isEqualTo(StatusPagamento.APROVADO);
    }

    @Test
    void recusarPagamento() {
        Orcamento o = Fixtures.orcamentoAprovado();
        o.recusarPagamento("pay-1", "cc_rejected");

        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.PAGAMENTO_RECUSADO);
        assertThat(o.getPagamento().getStatus()).isEqualTo(StatusPagamento.RECUSADO);
    }

    @Test
    void cancelarSoAntesDoPagamento() {
        Orcamento aguardando = Fixtures.orcamentoAguardando();
        aguardando.cancelar("timeout");
        assertThat(aguardando.getStatus()).isEqualTo(StatusOrcamento.CANCELADO);

        Orcamento pago = Fixtures.orcamentoPago();
        assertThatThrownBy(() -> pago.cancelar("x"))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("não pode ser cancelado");
    }

    @Test
    void estornoSoDePagamentoConfirmado() {
        Orcamento pago = Fixtures.orcamentoPago();
        pago.registrarEstorno("falha na execução");
        assertThat(pago.getStatus()).isEqualTo(StatusOrcamento.ESTORNADO);
        assertThat(pago.getPagamento().getStatus()).isEqualTo(StatusPagamento.ESTORNADO);

        Orcamento aprovado = Fixtures.orcamentoAprovado();
        assertThatThrownBy(() -> aprovado.registrarEstorno("x")).isInstanceOf(RegraDeNegocioException.class);
    }
}
