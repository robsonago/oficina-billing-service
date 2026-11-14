package br.com.fiap.challange.billing.domain.exception;

public class FalhaGatewayPagamentoException extends RuntimeException {

    public FalhaGatewayPagamentoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }

    public FalhaGatewayPagamentoException(String mensagem) {
        super(mensagem);
    }
}
