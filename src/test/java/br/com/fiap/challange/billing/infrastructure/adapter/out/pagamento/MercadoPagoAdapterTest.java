package br.com.fiap.challange.billing.infrastructure.adapter.out.pagamento;

import br.com.fiap.challange.billing.Fixtures;
import br.com.fiap.challange.billing.domain.exception.FalhaGatewayPagamentoException;
import br.com.fiap.challange.billing.domain.model.ConsultaPagamento;
import br.com.fiap.challange.billing.domain.model.ConsultaPagamento.Situacao;
import br.com.fiap.challange.billing.domain.model.Pagamento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MercadoPagoAdapterTest {

    private MockRestServiceServer servidor;
    private MercadoPagoAdapter adapter;
    private MercadoPagoProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MercadoPagoProperties();
        properties.setBaseUrl("https://api.mercadopago.com");
        properties.setAccessToken("TEST-123");
        properties.setNotificationUrl("https://oficina.exemplo/api/pagamentos/webhook");
        // o mock precisa ser o último a configurar o builder, senão a fábrica de
        // requisições com timeout substitui o mock e a chamada sai para a internet
        RestClient.Builder builder = MercadoPagoAdapter.configurar(RestClient.builder(), properties);
        servidor = MockRestServiceServer.bindTo(builder).build();
        adapter = new MercadoPagoAdapter(properties, builder.build());
    }

    @Test
    void criaPreferenciaComItensEReferenciaDoOrcamento() {
        servidor.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer TEST-123"))
                .andExpect(jsonPath("$.external_reference").value("orc-1"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].currency_id").value("BRL"))
                .andExpect(jsonPath("$.notification_url").value("https://oficina.exemplo/api/pagamentos/webhook"))
                .andExpect(jsonPath("$.payer.email").value("maria@email.com"))
                .andRespond(withSuccess("""
                        {"id":"pref-123","init_point":"https://mp/prod","sandbox_init_point":"https://mp/sandbox"}
                        """, MediaType.APPLICATION_JSON));

        Pagamento cobranca = adapter.criarCobranca(Fixtures.orcamentoAguardando());

        assertThat(cobranca.getPreferenciaId()).isEqualTo("pref-123");
        assertThat(cobranca.getLinkPagamento()).isEqualTo("https://mp/sandbox");
        servidor.verify();
    }

    @Test
    void foraDoSandboxUsaOInitPoint() {
        properties.setSandbox(false);
        servidor.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
                .andRespond(withSuccess("{\"id\":\"p\",\"init_point\":\"https://mp/prod\"}", MediaType.APPLICATION_JSON));

        assertThat(adapter.criarCobranca(Fixtures.orcamentoAguardando()).getLinkPagamento()).isEqualTo("https://mp/prod");
    }

    @Test
    void erroAoCriarPreferenciaViraFalhaDoGateway() {
        servidor.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> adapter.criarCobranca(Fixtures.orcamentoAguardando()))
                .isInstanceOf(FalhaGatewayPagamentoException.class);
    }

    @Test
    void consultaPagamento() {
        servidor.expect(requestTo("https://api.mercadopago.com/v1/payments/555"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":555,"status":"approved","status_detail":"accredited","external_reference":"orc-1"}
                        """, MediaType.APPLICATION_JSON));

        ConsultaPagamento consulta = adapter.consultar("555");

        assertThat(consulta.situacao()).isEqualTo(Situacao.APROVADO);
        assertThat(consulta.orcamentoId()).isEqualTo("orc-1");
        assertThat(consulta.detalhe()).isEqualTo("accredited");
    }

    @Test
    void erroNaConsulta() {
        servidor.expect(requestTo("https://api.mercadopago.com/v1/payments/1")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> adapter.consultar("1")).isInstanceOf(FalhaGatewayPagamentoException.class);
    }

    @Test
    void estornoTotal() {
        servidor.expect(requestTo("https://api.mercadopago.com/v1/payments/555/refunds"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Idempotency-Key", "estorno-555"))
                .andRespond(withSuccess("{\"id\":1}", MediaType.APPLICATION_JSON));

        adapter.estornar("555");

        servidor.verify();
    }

    @Test
    void erroNoEstorno() {
        servidor.expect(requestTo("https://api.mercadopago.com/v1/payments/555/refunds"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> adapter.estornar("555")).isInstanceOf(FalhaGatewayPagamentoException.class);
    }

    @Test
    void traduzStatusDoMercadoPago() {
        assertThat(MercadoPagoAdapter.traduzir("approved")).isEqualTo(Situacao.APROVADO);
        assertThat(MercadoPagoAdapter.traduzir("rejected")).isEqualTo(Situacao.RECUSADO);
        assertThat(MercadoPagoAdapter.traduzir("cancelled")).isEqualTo(Situacao.RECUSADO);
        assertThat(MercadoPagoAdapter.traduzir("refunded")).isEqualTo(Situacao.ESTORNADO);
        assertThat(MercadoPagoAdapter.traduzir("in_process")).isEqualTo(Situacao.PENDENTE);
    }
}
