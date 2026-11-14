package br.com.fiap.challange.billing.infrastructure.config;

import br.com.fiap.challange.billing.infrastructure.adapter.out.pagamento.MercadoPagoProperties;
import br.com.fiap.challange.billing.infrastructure.messaging.MensageriaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({MensageriaProperties.class, MercadoPagoProperties.class})
public class MensageriaConfig {
}
