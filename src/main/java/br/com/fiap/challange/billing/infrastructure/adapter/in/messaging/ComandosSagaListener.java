package br.com.fiap.challange.billing.infrastructure.adapter.in.messaging;

import br.com.fiap.challange.billing.domain.model.ClienteOrcamento;
import br.com.fiap.challange.billing.domain.model.ItemOrcamento;
import br.com.fiap.challange.billing.domain.model.TipoItem;
import br.com.fiap.challange.billing.domain.port.in.OrcamentoInputPort;
import br.com.fiap.challange.billing.domain.port.in.PagamentoInputPort;
import br.com.fiap.challange.billing.domain.port.in.command.GerarOrcamentoCommand;
import br.com.fiap.challange.billing.domain.port.out.MensagensProcessadasPort;
import br.com.fiap.challange.billing.infrastructure.messaging.Mensagem;
import br.com.fiap.challange.billing.infrastructure.messaging.ReceptorMensagens;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static br.com.fiap.challange.billing.domain.model.TiposMensagem.*;

/**
 * Entrada dos comandos enviados pelo orquestrador da saga (OS Service).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComandosSagaListener implements ReceptorMensagens {

    private final OrcamentoInputPort orcamentos;
    private final PagamentoInputPort pagamentos;
    private final MensagensProcessadasPort processadas;

    @Override
    public void receber(Mensagem mensagem) {
        if (processadas.jaProcessada(mensagem.id())) {
            log.info("Mensagem {} ({}) repetida, ignorando", mensagem.id(), mensagem.tipo());
            return;
        }
        log.info("Comando {} recebido saga={} os={}", mensagem.tipo(), mensagem.sagaId(), mensagem.ordemServicoId());
        JsonNode dados = mensagem.dados();
        switch (mensagem.tipo()) {
            case GERAR_ORCAMENTO -> orcamentos.gerar(paraGerarOrcamento(mensagem));
            case CANCELAR_ORCAMENTO -> orcamentos.cancelarPorSaga(mensagem.sagaId(), mensagem.ordemServicoId(),
                    dados.path("motivo").asText("Cancelado pela saga"));
            case ESTORNAR_PAGAMENTO -> pagamentos.estornarPorSaga(mensagem.sagaId(), mensagem.ordemServicoId(),
                    dados.path("motivo").asText("Compensação da saga"));
            default -> log.warn("Tipo de mensagem desconhecido para o Billing: {}", mensagem.tipo());
        }
        processadas.registrar(mensagem.id(), mensagem.tipo());
    }

    private GerarOrcamentoCommand paraGerarOrcamento(Mensagem mensagem) {
        JsonNode dados = mensagem.dados();
        JsonNode cliente = dados.path("cliente");
        List<ItemOrcamento> itens = new ArrayList<>();
        for (JsonNode item : dados.path("itens")) {
            itens.add(new ItemOrcamento(
                    TipoItem.valueOf(item.path("tipo").asText()),
                    item.hasNonNull("referenciaId") ? item.path("referenciaId").asLong() : null,
                    item.path("descricao").asText(),
                    item.path("quantidade").asInt(1),
                    item.path("precoUnitario").decimalValue()));
        }
        return new GerarOrcamentoCommand(
                mensagem.sagaId(),
                mensagem.ordemServicoId(),
                dados.path("numeroOS").asText(),
                new ClienteOrcamento(cliente.path("nome").asText(null), cliente.path("documento").asText(null),
                        cliente.path("email").asText(null)),
                itens);
    }
}
