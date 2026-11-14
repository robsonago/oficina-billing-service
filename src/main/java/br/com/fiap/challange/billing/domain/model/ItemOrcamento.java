package br.com.fiap.challange.billing.domain.model;

import java.math.BigDecimal;

public record ItemOrcamento(TipoItem tipo, Long referenciaId, String descricao,
                            int quantidade, BigDecimal precoUnitario) {

    public BigDecimal subtotal() {
        return precoUnitario.multiply(BigDecimal.valueOf(quantidade));
    }
}
