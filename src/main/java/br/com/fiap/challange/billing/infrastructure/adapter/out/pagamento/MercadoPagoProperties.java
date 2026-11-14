package br.com.fiap.challange.billing.infrastructure.adapter.out.pagamento;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "pagamento.mercadopago")
public class MercadoPagoProperties {

    private String baseUrl = "https://api.mercadopago.com";

    /** Access token da aplicação (em sandbox começa com TEST-). */
    private String accessToken;

    /** URL pública do webhook, informada em cada preferência criada. */
    private String notificationUrl;

    /** Chave secreta do webhook, para validar o cabeçalho x-signature. Vazia = não valida. */
    private String webhookSecret;

    /** Em sandbox o link de pagamento é o sandbox_init_point. */
    private boolean sandbox = true;

    private int timeoutSegundos = 10;
}
