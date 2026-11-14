package br.com.fiap.challange.billing.domain.port.out;

import br.com.fiap.challange.billing.domain.model.ConsultaPagamento;
import br.com.fiap.challange.billing.domain.model.Orcamento;
import br.com.fiap.challange.billing.domain.model.Pagamento;

public interface GatewayPagamentoPort {

    /** Cria a cobrança (preferência de checkout) e devolve o link para o cliente pagar. */
    Pagamento criarCobranca(Orcamento orcamento);

    ConsultaPagamento consultar(String pagamentoId);

    void estornar(String pagamentoId);
}
