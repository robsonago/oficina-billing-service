package br.com.fiap.challange.billing.infrastructure.adapter.in.rest;

import br.com.fiap.challange.billing.infrastructure.adapter.out.pagamento.MercadoPagoProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Valida o cabeçalho x-signature enviado pelo Mercado Pago nas notificações.
 * O manifesto assinado é "id:{data.id};request-id:{x-request-id};ts:{ts};"
 * com HMAC-SHA256 usando a chave secreta configurada no painel.
 */
@Component
@RequiredArgsConstructor
public class AssinaturaWebhookValidator {

    private final MercadoPagoProperties properties;

    public boolean valida(String xSignature, String xRequestId, String dataId) {
        String segredo = properties.getWebhookSecret();
        if (segredo == null || segredo.isBlank()) {
            return true; // validação desligada (ambiente local / modo simulado)
        }
        if (xSignature == null || dataId == null) {
            return false;
        }
        String ts = null;
        String v1 = null;
        for (String parte : xSignature.split(",")) {
            String[] chaveValor = parte.trim().split("=", 2);
            if (chaveValor.length == 2 && "ts".equals(chaveValor[0])) {
                ts = chaveValor[1];
            } else if (chaveValor.length == 2 && "v1".equals(chaveValor[0])) {
                v1 = chaveValor[1];
            }
        }
        if (ts == null || v1 == null) {
            return false;
        }
        StringBuilder manifesto = new StringBuilder("id:").append(dataId.toLowerCase()).append(';');
        if (xRequestId != null && !xRequestId.isBlank()) {
            manifesto.append("request-id:").append(xRequestId).append(';');
        }
        manifesto.append("ts:").append(ts).append(';');
        String calculado = hmac(segredo, manifesto.toString());
        return MessageDigest.isEqual(calculado.getBytes(StandardCharsets.UTF_8), v1.getBytes(StandardCharsets.UTF_8));
    }

    static String hmac(String segredo, String conteudo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(conteudo.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível calcular a assinatura do webhook", e);
        }
    }
}
