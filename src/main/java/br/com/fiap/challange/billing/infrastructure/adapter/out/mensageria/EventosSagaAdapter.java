package br.com.fiap.challange.billing.infrastructure.adapter.out.mensageria;

import br.com.fiap.challange.billing.domain.port.out.EventosSagaPort;
import br.com.fiap.challange.billing.infrastructure.messaging.CanalMensageria;
import br.com.fiap.challange.billing.infrastructure.messaging.Mensagem;
import br.com.fiap.challange.billing.infrastructure.messaging.Rastreamento;
import br.com.fiap.challange.billing.infrastructure.metrics.BillingMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class EventosSagaAdapter implements EventosSagaPort {

    static final String DESTINO = "os-eventos";

    private final CanalMensageria canal;
    private final ObjectMapper objectMapper;
    private final BillingMetrics metrics;

    @Override
    public void publicar(String tipo, String sagaId, Long ordemServicoId, Map<String, Object> dados) {
        Mensagem mensagem = Mensagem.nova(tipo, sagaId, ordemServicoId, objectMapper.valueToTree(dados));
        canal.enviar(DESTINO, mensagem, Rastreamento.cabecalhosAtuais(sagaId));
        metrics.eventoPublicado(tipo);
    }
}
