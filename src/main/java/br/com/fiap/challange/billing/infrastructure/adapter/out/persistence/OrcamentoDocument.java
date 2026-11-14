package br.com.fiap.challange.billing.infrastructure.adapter.out.persistence;

import br.com.fiap.challange.billing.domain.model.ClienteOrcamento;
import br.com.fiap.challange.billing.domain.model.HistoricoOrcamento;
import br.com.fiap.challange.billing.domain.model.ItemOrcamento;
import br.com.fiap.challange.billing.domain.model.StatusOrcamento;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Um orçamento é naturalmente um documento: itens, cobrança e histórico
 * vivem juntos e são sempre lidos de uma vez — por isso o Billing usa
 * MongoDB em vez de tabelas relacionais.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "orcamentos")
public class OrcamentoDocument {

    @Id
    private String id;

    @Indexed
    private Long ordemServicoId;

    private String numeroOS;

    @Indexed(unique = true)
    private String sagaId;

    private ClienteOrcamento cliente;
    private List<ItemOrcamento> itens;
    private BigDecimal valorTotal;
    private StatusOrcamento status;
    private PagamentoDocument pagamento;
    private String observacao;
    private List<HistoricoOrcamento> historico;
    private Instant criadoEm;
    private Instant atualizadoEm;

    @Version
    private Long versao;
}
