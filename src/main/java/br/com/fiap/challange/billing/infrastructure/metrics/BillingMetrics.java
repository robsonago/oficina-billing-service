package br.com.fiap.challange.billing.infrastructure.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Métricas de negócio expostas em /actuator/prometheus (coletadas pelo nri-prometheus). */
@Component
@RequiredArgsConstructor
public class BillingMetrics {

    private final MeterRegistry registry;

    public void eventoPublicado(String tipo) {
        registry.counter("oficina.billing.eventos", "tipo", tipo).increment();
    }

    public void erroIntegracao(String integracao) {
        registry.counter("oficina.integracoes.erros", "integracao", integracao).increment();
    }
}
