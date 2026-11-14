package br.com.fiap.challange.billing.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotNull;

public record DecisaoOrcamentoRequest(@NotNull(message = "Informe se o orçamento foi aprovado") Boolean aprovado,
                                      String observacao) {
}
