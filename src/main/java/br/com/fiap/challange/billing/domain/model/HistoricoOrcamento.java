package br.com.fiap.challange.billing.domain.model;

import java.time.Instant;

public record HistoricoOrcamento(StatusOrcamento status, String descricao, Instant ocorridoEm) {
}
