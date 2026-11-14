package br.com.fiap.challange.billing.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Saída "crua" para o broker. Quem publica eventos de negócio usa os ports
 * do domínio; esta interface só existe para trocar Pub/Sub por uma
 * implementação em memória nos testes.
 */
public interface CanalMensageria {

    void enviar(String destino, Mensagem mensagem, Map<String, String> cabecalhos);

    /**
     * Publica o JSON como está, sem o envelope da saga. Usado no tópico de
     * notificações, cujo formato já era consumido pela function de e-mail
     * desde a Fase 3.
     */
    void enviarSemEnvelope(String destino, String tipo, JsonNode conteudo, Map<String, String> cabecalhos);
}
