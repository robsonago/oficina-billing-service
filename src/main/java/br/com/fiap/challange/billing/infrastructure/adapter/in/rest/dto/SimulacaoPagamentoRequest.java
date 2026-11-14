package br.com.fiap.challange.billing.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotNull;

public record SimulacaoPagamentoRequest(@NotNull(message = "Informe se o pagamento foi aprovado") Boolean aprovado) {
}
