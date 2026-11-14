package br.com.fiap.challange.billing.domain.model;

/**
 * Nomes dos comandos que o Billing recebe e dos eventos que ele devolve
 * para o orquestrador. Precisam bater com os do OS Service.
 */
public final class TiposMensagem {

    // comandos recebidos
    public static final String GERAR_ORCAMENTO = "GERAR_ORCAMENTO";
    public static final String CANCELAR_ORCAMENTO = "CANCELAR_ORCAMENTO";
    public static final String ESTORNAR_PAGAMENTO = "ESTORNAR_PAGAMENTO";

    // eventos publicados
    public static final String ORCAMENTO_GERADO = "ORCAMENTO_GERADO";
    public static final String ORCAMENTO_FALHOU = "ORCAMENTO_FALHOU";
    public static final String ORCAMENTO_APROVADO = "ORCAMENTO_APROVADO";
    public static final String ORCAMENTO_REJEITADO = "ORCAMENTO_REJEITADO";
    public static final String ORCAMENTO_CANCELADO = "ORCAMENTO_CANCELADO";
    public static final String PAGAMENTO_APROVADO = "PAGAMENTO_APROVADO";
    public static final String PAGAMENTO_RECUSADO = "PAGAMENTO_RECUSADO";
    public static final String PAGAMENTO_ESTORNADO = "PAGAMENTO_ESTORNADO";

    private TiposMensagem() {
    }
}
