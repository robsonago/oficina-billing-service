package br.com.fiap.challange.billing.application;

import br.com.fiap.challange.billing.Fixtures;
import br.com.fiap.challange.billing.application.usecase.PagamentoUseCase;
import br.com.fiap.challange.billing.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.challange.billing.domain.model.ConsultaPagamento;
import br.com.fiap.challange.billing.domain.model.ConsultaPagamento.Situacao;
import br.com.fiap.challange.billing.domain.model.Orcamento;
import br.com.fiap.challange.billing.domain.model.StatusOrcamento;
import br.com.fiap.challange.billing.domain.port.in.OrcamentoInputPort;
import br.com.fiap.challange.billing.domain.port.out.EventosSagaPort;
import br.com.fiap.challange.billing.domain.port.out.GatewayPagamentoPort;
import br.com.fiap.challange.billing.domain.port.out.OrcamentoRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static br.com.fiap.challange.billing.domain.model.TiposMensagem.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagamentoUseCaseTest {

    @Mock
    OrcamentoRepositoryPort repository;
    @Mock
    GatewayPagamentoPort gateway;
    @Mock
    EventosSagaPort eventos;
    @Mock
    OrcamentoInputPort orcamentos;

    @InjectMocks
    PagamentoUseCase useCase;

    @Test
    void pagamentoAprovadoAvisaOOrquestrador() {
        Orcamento o = Fixtures.orcamentoAprovado();
        when(gateway.consultar("pay-1")).thenReturn(new ConsultaPagamento("pay-1", "orc-1", Situacao.APROVADO, "accredited"));
        when(repository.buscarPorId("orc-1")).thenReturn(Optional.of(o));

        useCase.processarNotificacao("pay-1");

        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.PAGO);
        verify(eventos).publicar(eq(PAGAMENTO_APROVADO), eq("saga-1"), eq(10L),
                argThat(d -> "pay-1".equals(d.get("pagamentoId"))));
    }

    @Test
    void pagamentoRecusadoAvisaOOrquestrador() {
        Orcamento o = Fixtures.orcamentoAprovado();
        when(gateway.consultar("pay-2")).thenReturn(new ConsultaPagamento("pay-2", "orc-1", Situacao.RECUSADO, null));
        when(repository.buscarPorId("orc-1")).thenReturn(Optional.of(o));

        useCase.processarNotificacao("pay-2");

        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.PAGAMENTO_RECUSADO);
        verify(eventos).publicar(eq(PAGAMENTO_RECUSADO), eq("saga-1"), eq(10L),
                argThat(d -> "recusado pelo emissor".equals(d.get("motivo"))));
    }

    @Test
    void pagamentoPendenteNaoMudaNada() {
        Orcamento o = Fixtures.orcamentoAprovado();
        when(gateway.consultar("pay-3")).thenReturn(new ConsultaPagamento("pay-3", "orc-1", Situacao.PENDENTE, "pending"));
        when(repository.buscarPorId("orc-1")).thenReturn(Optional.of(o));

        useCase.processarNotificacao("pay-3");

        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.APROVADO);
        verifyNoInteractions(eventos);
    }

    @Test
    void notificacaoRepetidaDeOrcamentoJaPagoEIgnorada() {
        when(gateway.consultar("pay-99")).thenReturn(new ConsultaPagamento("pay-99", "orc-1", Situacao.APROVADO, null));
        when(repository.buscarPorId("orc-1")).thenReturn(Optional.of(Fixtures.orcamentoPago()));

        useCase.processarNotificacao("pay-99");

        verify(repository, never()).salvar(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void pagamentoDeOrcamentoInexistente() {
        when(gateway.consultar("pay-1")).thenReturn(new ConsultaPagamento("pay-1", "nao-existe", Situacao.APROVADO, null));
        when(repository.buscarPorId("nao-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.processarNotificacao("pay-1"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void estornoDePagamentoConfirmado() {
        Orcamento o = Fixtures.orcamentoPago();
        when(repository.buscarPorSagaId("saga-1")).thenReturn(Optional.of(o));

        useCase.estornarPorSaga("saga-1", 10L, "Falha na execução");

        verify(gateway).estornar("pay-99");
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.ESTORNADO);
        verify(eventos).publicar(eq(PAGAMENTO_ESTORNADO), eq("saga-1"), eq(10L), anyMap());
    }

    @Test
    void estornoRepetidoNaoChamaOGatewayDeNovo() {
        Orcamento o = Fixtures.orcamentoPago();
        o.registrarEstorno("primeira vez");
        when(repository.buscarPorSagaId("saga-1")).thenReturn(Optional.of(o));

        useCase.estornarPorSaga("saga-1", 10L, "de novo");

        verifyNoInteractions(gateway);
        verify(eventos).publicar(eq(PAGAMENTO_ESTORNADO), eq("saga-1"), eq(10L), anyMap());
    }

    @Test
    void semPagamentoOEstornoViraCancelamento() {
        when(repository.buscarPorSagaId("saga-1")).thenReturn(Optional.of(Fixtures.orcamentoAprovado()));

        useCase.estornarPorSaga("saga-1", 10L, "Falha");

        verify(orcamentos).cancelarPorSaga("saga-1", 10L, "Falha");
        verifyNoInteractions(gateway);
    }

    @Test
    void sagaSemOrcamentoTambemViraCancelamento() {
        when(repository.buscarPorSagaId("saga-z")).thenReturn(Optional.empty());

        useCase.estornarPorSaga("saga-z", 1L, "Falha");

        verify(orcamentos).cancelarPorSaga("saga-z", 1L, "Falha");
    }
}
