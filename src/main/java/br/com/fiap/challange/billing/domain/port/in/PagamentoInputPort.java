package br.com.fiap.challange.billing.domain.port.in;

public interface PagamentoInputPort {
    void processarNotificacao(String pagamentoId);
    void estornarPorSaga(String sagaId, Long ordemServicoId, String motivo);
}
