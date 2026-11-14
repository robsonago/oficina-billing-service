package br.com.fiap.challange.billing.domain.port.out;

import br.com.fiap.challange.billing.domain.model.Orcamento;

public interface NotificacaoPort {

    void orcamentoDisponivel(Orcamento orcamento);
}
