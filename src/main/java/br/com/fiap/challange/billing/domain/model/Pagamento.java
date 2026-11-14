package br.com.fiap.challange.billing.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cobrança criada no Mercado Pago quando o orçamento é aprovado.
 * {@code id} só é preenchido quando o pagamento de fato acontece (vem no webhook).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Pagamento {

    private String id;
    private String preferenciaId;
    private String linkPagamento;
    private BigDecimal valor;
    private StatusPagamento status;
    private Instant criadoEm;
    private Instant atualizadoEm;
}
