package br.com.fiap.challange.billing.domain.model;

/**
 * Situação de um pagamento consultada no gateway.
 *
 * @param pagamentoId      id do pagamento no Mercado Pago
 * @param orcamentoId      external_reference enviada na criação da cobrança
 * @param situacao         situação já traduzida para o nosso domínio
 * @param detalhe          motivo informado pelo gateway (status_detail)
 */
public record ConsultaPagamento(String pagamentoId, String orcamentoId, Situacao situacao, String detalhe) {

    public enum Situacao {
        APROVADO,
        RECUSADO,
        PENDENTE,
        ESTORNADO
    }
}
