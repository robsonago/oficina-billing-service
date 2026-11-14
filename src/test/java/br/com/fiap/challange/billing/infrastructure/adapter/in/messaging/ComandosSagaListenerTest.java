package br.com.fiap.challange.billing.infrastructure.adapter.in.messaging;

import br.com.fiap.challange.billing.domain.model.TipoItem;
import br.com.fiap.challange.billing.domain.port.in.OrcamentoInputPort;
import br.com.fiap.challange.billing.domain.port.in.PagamentoInputPort;
import br.com.fiap.challange.billing.domain.port.in.command.GerarOrcamentoCommand;
import br.com.fiap.challange.billing.domain.port.out.MensagensProcessadasPort;
import br.com.fiap.challange.billing.infrastructure.messaging.Mensagem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComandosSagaListenerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock
    OrcamentoInputPort orcamentos;
    @Mock
    PagamentoInputPort pagamentos;
    @Mock
    MensagensProcessadasPort processadas;

    @InjectMocks
    ComandosSagaListener listener;

    @Test
    void gerarOrcamentoMontaOComandoAPartirDoJson() throws Exception {
        var dados = mapper.readTree("""
                {"numeroOS":"OS-1","cliente":{"nome":"Ana","documento":"123","email":"ana@x.com"},
                 "itens":[{"tipo":"SERVICO","referenciaId":4,"descricao":"Alinhamento","quantidade":1,"precoUnitario":120.50},
                          {"tipo":"PECA","descricao":"Parafuso","quantidade":4,"precoUnitario":2}]}
                """);
        Mensagem m = Mensagem.nova("GERAR_ORCAMENTO", "saga-1", 5L, dados);

        listener.receber(m);

        ArgumentCaptor<GerarOrcamentoCommand> captor = ArgumentCaptor.forClass(GerarOrcamentoCommand.class);
        verify(orcamentos).gerar(captor.capture());
        GerarOrcamentoCommand cmd = captor.getValue();
        assertThat(cmd.numeroOS()).isEqualTo("OS-1");
        assertThat(cmd.cliente().email()).isEqualTo("ana@x.com");
        assertThat(cmd.itens()).hasSize(2);
        assertThat(cmd.itens().get(0).tipo()).isEqualTo(TipoItem.SERVICO);
        assertThat(cmd.itens().get(1).referenciaId()).isNull();
        verify(processadas).registrar(m.id(), "GERAR_ORCAMENTO");
    }

    @Test
    void cancelarEEstornar() throws Exception {
        listener.receber(Mensagem.nova("CANCELAR_ORCAMENTO", "s", 1L, mapper.readTree("{\"motivo\":\"timeout\"}")));
        listener.receber(Mensagem.nova("ESTORNAR_PAGAMENTO", "s", 1L, mapper.readTree("{}")));

        verify(orcamentos).cancelarPorSaga("s", 1L, "timeout");
        verify(pagamentos).estornarPorSaga("s", 1L, "Compensação da saga");
    }

    @Test
    void mensagemRepetidaNaoReprocessa() throws Exception {
        Mensagem m = Mensagem.nova("CANCELAR_ORCAMENTO", "s", 1L, mapper.readTree("{}"));
        when(processadas.jaProcessada(m.id())).thenReturn(true);

        listener.receber(m);

        verifyNoInteractions(orcamentos, pagamentos);
        verify(processadas, never()).registrar(any(), any());
    }

    @Test
    void tipoDesconhecidoSoEhRegistrado() throws Exception {
        Mensagem m = Mensagem.nova("OUTRA_COISA", "s", 1L, mapper.readTree("{}"));

        listener.receber(m);

        verifyNoInteractions(orcamentos, pagamentos);
        verify(processadas).registrar(m.id(), "OUTRA_COISA");
    }
}
