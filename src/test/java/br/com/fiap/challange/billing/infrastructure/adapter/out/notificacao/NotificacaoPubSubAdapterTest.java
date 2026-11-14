package br.com.fiap.challange.billing.infrastructure.adapter.out.notificacao;

import br.com.fiap.challange.billing.Fixtures;
import br.com.fiap.challange.billing.domain.model.ClienteOrcamento;
import br.com.fiap.challange.billing.domain.model.Orcamento;
import br.com.fiap.challange.billing.infrastructure.messaging.CanalEmMemoria;
import br.com.fiap.challange.billing.infrastructure.messaging.CanalMensageria;
import br.com.fiap.challange.billing.infrastructure.metrics.BillingMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class NotificacaoPubSubAdapterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final BillingMetrics metrics = new BillingMetrics(registry);

    @Test
    void publicaNoFormatoQueAFunctionDeEmailEspera() {
        CanalEmMemoria canal = new CanalEmMemoria();
        new NotificacaoPubSubAdapter(canal, new ObjectMapper(), metrics).orcamentoDisponivel(Fixtures.orcamentoAguardando());

        var enviada = canal.enviadas().getFirst();
        assertThat(enviada.destino()).isEqualTo("notificacoes");
        assertThat(enviada.mensagem().dados().path("destinatario").asText()).isEqualTo("maria@email.com");
        assertThat(enviada.mensagem().dados().path("valorTotal").asText()).isEqualTo("151.00");
    }

    @Test
    void clienteSemEmailNaoRecebeNotificacao() {
        CanalEmMemoria canal = new CanalEmMemoria();
        Orcamento o = Fixtures.orcamentoAguardando();
        o.setCliente(new ClienteOrcamento("Sem Email", "1", " "));

        new NotificacaoPubSubAdapter(canal, new ObjectMapper(), metrics).orcamentoDisponivel(o);

        assertThat(canal.enviadas()).isEmpty();
    }

    @Test
    void falhaAoPublicarNaoDerrubaOFluxo() {
        CanalMensageria canal = mock(CanalMensageria.class);
        doThrow(new IllegalStateException("broker fora")).when(canal).enviarSemEnvelope(any(), any(), any(), any());

        new NotificacaoPubSubAdapter(canal, new ObjectMapper(), metrics).orcamentoDisponivel(Fixtures.orcamentoAguardando());

        assertThat(registry.counter("oficina.integracoes.erros", "integracao", "notificacao").count()).isEqualTo(1.0);
    }
}
