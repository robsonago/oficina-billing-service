package br.com.fiap.challange.billing.infrastructure.adapter.out.pagamento;

import br.com.fiap.challange.billing.Fixtures;
import br.com.fiap.challange.billing.domain.exception.FalhaGatewayPagamentoException;
import br.com.fiap.challange.billing.domain.model.ConsultaPagamento.Situacao;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PagamentoSimuladoAdapterTest {

    private final PagamentoSimuladoAdapter adapter = new PagamentoSimuladoAdapter("http://local/simulacao");

    @Test
    void cobrancaApontaParaOEndpointDeSimulacao() {
        assertThat(adapter.criarCobranca(Fixtures.orcamentoAguardando()).getLinkPagamento())
                .isEqualTo("http://local/simulacao/orc-1");
    }

    @Test
    void registraConsultaEEstorna() {
        String aprovado = adapter.registrarPagamento("orc-1", true);
        String recusado = adapter.registrarPagamento("orc-2", false);

        assertThat(adapter.consultar(aprovado).situacao()).isEqualTo(Situacao.APROVADO);
        assertThat(adapter.consultar(recusado).situacao()).isEqualTo(Situacao.RECUSADO);

        adapter.estornar(aprovado);
        assertThat(adapter.consultar(aprovado).situacao()).isEqualTo(Situacao.ESTORNADO);
    }

    @Test
    void pagamentoDesconhecido() {
        assertThatThrownBy(() -> adapter.consultar("nada")).isInstanceOf(FalhaGatewayPagamentoException.class);
    }
}
