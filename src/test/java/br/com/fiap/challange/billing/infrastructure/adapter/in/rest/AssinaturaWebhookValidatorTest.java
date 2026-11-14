package br.com.fiap.challange.billing.infrastructure.adapter.in.rest;

import br.com.fiap.challange.billing.infrastructure.adapter.out.pagamento.MercadoPagoProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssinaturaWebhookValidatorTest {

    private AssinaturaWebhookValidator validator(String segredo) {
        MercadoPagoProperties properties = new MercadoPagoProperties();
        properties.setWebhookSecret(segredo);
        return new AssinaturaWebhookValidator(properties);
    }

    @Test
    void semSegredoConfiguradoAceitaTudo() {
        assertThat(validator(null).valida(null, null, null)).isTrue();
        assertThat(validator("").valida("x", "y", "z")).isTrue();
    }

    @Test
    void aceitaAssinaturaCorreta() {
        String v1 = AssinaturaWebhookValidator.hmac("segredo", "id:123;request-id:req-1;ts:1700000000;");

        assertThat(validator("segredo").valida("ts=1700000000,v1=" + v1, "req-1", "123")).isTrue();
    }

    @Test
    void aceitaSemRequestId() {
        String v1 = AssinaturaWebhookValidator.hmac("segredo", "id:abc;ts:1;");

        assertThat(validator("segredo").valida("ts=1, v1=" + v1, null, "ABC")).isTrue();
    }

    @Test
    void recusaAssinaturaErradaOuIncompleta() {
        AssinaturaWebhookValidator v = validator("segredo");

        assertThat(v.valida("ts=1,v1=deadbeef", "req", "123")).isFalse();
        assertThat(v.valida("ts=1", "req", "123")).isFalse();
        assertThat(v.valida("lixo", "req", "123")).isFalse();
        assertThat(v.valida(null, "req", "123")).isFalse();
        assertThat(v.valida("ts=1,v1=aa", "req", null)).isFalse();
    }
}
