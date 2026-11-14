package br.com.fiap.challange.billing.domain.port.out;

import java.util.Map;

/** Respostas do Billing para o orquestrador da saga (OS Service). */
public interface EventosSagaPort {

    void publicar(String tipo, String sagaId, Long ordemServicoId, Map<String, Object> dados);
}
