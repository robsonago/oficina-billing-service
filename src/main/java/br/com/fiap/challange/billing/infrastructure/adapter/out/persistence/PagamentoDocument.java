package br.com.fiap.challange.billing.infrastructure.adapter.out.persistence;

import br.com.fiap.challange.billing.domain.model.StatusPagamento;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagamentoDocument {
    private String id;
    private String preferenciaId;
    private String linkPagamento;
    private BigDecimal valor;
    private StatusPagamento status;
    private Instant criadoEm;
    private Instant atualizadoEm;
}
