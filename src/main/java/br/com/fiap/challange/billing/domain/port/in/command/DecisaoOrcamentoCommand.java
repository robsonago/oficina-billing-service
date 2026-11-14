package br.com.fiap.challange.billing.domain.port.in.command;

/**
 * @param documentoCliente preenchido quando quem decide é o próprio cliente
 *                         (token emitido pela function de CPF); nulo para funcionário
 */
public record DecisaoOrcamentoCommand(String orcamentoId, boolean aprovado, String observacao,
                                      String documentoCliente) {
}
