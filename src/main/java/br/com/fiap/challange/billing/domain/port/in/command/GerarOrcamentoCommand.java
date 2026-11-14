package br.com.fiap.challange.billing.domain.port.in.command;

import br.com.fiap.challange.billing.domain.model.ClienteOrcamento;
import br.com.fiap.challange.billing.domain.model.ItemOrcamento;

import java.util.List;

public record GerarOrcamentoCommand(String sagaId, Long ordemServicoId, String numeroOS,
                                    ClienteOrcamento cliente, List<ItemOrcamento> itens) {
}
