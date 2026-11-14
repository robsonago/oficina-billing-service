package br.com.fiap.challange.billing.domain.model;

/** Cópia dos dados do cliente no momento do orçamento (o cadastro fica no OS Service). */
public record ClienteOrcamento(String nome, String documento, String email) {
}
