package br.com.fiap.challange.billing;

import br.com.fiap.challange.billing.domain.model.ClienteOrcamento;
import br.com.fiap.challange.billing.domain.model.ItemOrcamento;
import br.com.fiap.challange.billing.domain.model.Orcamento;
import br.com.fiap.challange.billing.domain.model.Pagamento;
import br.com.fiap.challange.billing.domain.model.StatusPagamento;
import br.com.fiap.challange.billing.domain.model.TipoItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class Fixtures {

    public static final String DOCUMENTO = "52998224725";

    private Fixtures() {
    }

    public static ClienteOrcamento cliente() {
        return new ClienteOrcamento("Maria Souza", DOCUMENTO, "maria@email.com");
    }

    public static List<ItemOrcamento> itens() {
        return List.of(
                new ItemOrcamento(TipoItem.SERVICO, 1L, "Troca de óleo", 1, new BigDecimal("80.00")),
                new ItemOrcamento(TipoItem.PECA, 7L, "Filtro de óleo", 2, new BigDecimal("35.50")));
    }

    public static Orcamento orcamentoAguardando() {
        Orcamento o = Orcamento.novo(10L, "OS-2026-ABC", "saga-1", cliente(), itens());
        o.setId("orc-1");
        return o;
    }

    public static Orcamento orcamentoAprovado() {
        Orcamento o = orcamentoAguardando();
        o.aprovar(cobranca(), null);
        return o;
    }

    public static Orcamento orcamentoPago() {
        Orcamento o = orcamentoAprovado();
        o.confirmarPagamento("pay-99");
        return o;
    }

    public static Pagamento cobranca() {
        return Pagamento.builder().preferenciaId("pref-1").linkPagamento("https://mp/checkout/pref-1")
                .valor(new BigDecimal("151.00")).status(StatusPagamento.PENDENTE).criadoEm(Instant.now()).build();
    }
}
