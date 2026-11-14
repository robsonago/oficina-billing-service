package br.com.fiap.challange.billing.domain.port.out;

/**
 * Controle de idempotência: o Pub/Sub entrega "pelo menos uma vez", então a
 * mesma mensagem pode chegar mais de uma vez.
 */
public interface MensagensProcessadasPort {

    boolean jaProcessada(String mensagemId);

    void registrar(String mensagemId, String tipo);
}
