package br.com.fiap.challange.billing.application;

import br.com.fiap.challange.billing.Fixtures;
import br.com.fiap.challange.billing.application.usecase.OrcamentoUseCase;
import br.com.fiap.challange.billing.domain.exception.AcessoNegadoException;
import br.com.fiap.challange.billing.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.challange.billing.domain.exception.RegraDeNegocioException;
import br.com.fiap.challange.billing.domain.model.Orcamento;
import br.com.fiap.challange.billing.domain.model.StatusOrcamento;
import br.com.fiap.challange.billing.domain.port.in.command.DecisaoOrcamentoCommand;
import br.com.fiap.challange.billing.domain.port.in.command.GerarOrcamentoCommand;
import br.com.fiap.challange.billing.domain.port.out.EventosSagaPort;
import br.com.fiap.challange.billing.domain.port.out.GatewayPagamentoPort;
import br.com.fiap.challange.billing.domain.port.out.NotificacaoPort;
import br.com.fiap.challange.billing.domain.port.out.OrcamentoRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static br.com.fiap.challange.billing.domain.model.TiposMensagem.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrcamentoUseCaseTest {

    @Mock
    OrcamentoRepositoryPort repository;
    @Mock
    GatewayPagamentoPort gateway;
    @Mock
    EventosSagaPort eventos;
    @Mock
    NotificacaoPort notificacao;

    @InjectMocks
    OrcamentoUseCase useCase;

    private GerarOrcamentoCommand comando() {
        return new GerarOrcamentoCommand("saga-1", 10L, "OS-2026-ABC", Fixtures.cliente(), Fixtures.itens());
    }

    @Test
    void geraOrcamentoNotificaEPublicaGerado() {
        when(repository.buscarPorSagaId("saga-1")).thenReturn(Optional.empty());
        when(repository.salvar(any())).thenAnswer(inv -> {
            Orcamento o = inv.getArgument(0);
            o.setId("orc-1");
            return o;
        });

        useCase.gerar(comando());

        verify(notificacao).orcamentoDisponivel(any());
        verify(eventos).publicar(eq(ORCAMENTO_GERADO), eq("saga-1"), eq(10L),
                argThat(d -> "orc-1".equals(d.get("orcamentoId"))));
    }

    @Test
    void comandoRepetidoSoReenviaAResposta() {
        when(repository.buscarPorSagaId("saga-1")).thenReturn(Optional.of(Fixtures.orcamentoAguardando()));

        useCase.gerar(comando());

        verify(repository, never()).salvar(any());
        verify(notificacao, never()).orcamentoDisponivel(any());
        verify(eventos).publicar(eq(ORCAMENTO_GERADO), eq("saga-1"), eq(10L), anyMap());
    }

    @Test
    void osSemItensGeraEventoDeFalha() {
        when(repository.buscarPorSagaId("saga-1")).thenReturn(Optional.empty());

        useCase.gerar(new GerarOrcamentoCommand("saga-1", 10L, "OS-1", Fixtures.cliente(), List.of()));

        verify(eventos).publicar(eq(ORCAMENTO_FALHOU), eq("saga-1"), eq(10L),
                argThat(d -> d.get("motivo").toString().contains("não tem serviços")));
        verify(repository, never()).salvar(any());
    }

    @Test
    void aprovarCriaCobrancaEPublicaAprovado() {
        when(repository.buscarPorId("orc-1")).thenReturn(Optional.of(Fixtures.orcamentoAguardando()));
        when(gateway.criarCobranca(any())).thenReturn(Fixtures.cobranca());
        when(repository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        Orcamento aprovado = useCase.decidir(new DecisaoOrcamentoCommand("orc-1", true, null, Fixtures.DOCUMENTO));

        assertThat(aprovado.getStatus()).isEqualTo(StatusOrcamento.APROVADO);
        verify(eventos).publicar(eq(ORCAMENTO_APROVADO), eq("saga-1"), eq(10L),
                argThat(d -> "https://mp/checkout/pref-1".equals(d.get("linkPagamento"))));
    }

    @Test
    void rejeitarPublicaRejeitado() {
        when(repository.buscarPorId("orc-1")).thenReturn(Optional.of(Fixtures.orcamentoAguardando()));
        when(repository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        Orcamento rejeitado = useCase.decidir(new DecisaoOrcamentoCommand("orc-1", false, "Caro demais", null));

        assertThat(rejeitado.getStatus()).isEqualTo(StatusOrcamento.REJEITADO);
        verify(eventos).publicar(eq(ORCAMENTO_REJEITADO), eq("saga-1"), eq(10L),
                argThat(d -> "Caro demais".equals(d.get("motivo"))));
        verifyNoInteractions(gateway);
    }

    @Test
    void rejeitarSemMotivoUsaTextoPadrao() {
        when(repository.buscarPorId("orc-1")).thenReturn(Optional.of(Fixtures.orcamentoAguardando()));
        when(repository.salvar(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase.decidir(new DecisaoOrcamentoCommand("orc-1", false, null, null));

        verify(eventos).publicar(eq(ORCAMENTO_REJEITADO), any(), any(),
                argThat(d -> "Rejeitado pelo cliente".equals(d.get("motivo"))));
    }

    @Test
    void naoDecideOrcamentoJaDecidido() {
        when(repository.buscarPorId("orc-1")).thenReturn(Optional.of(Fixtures.orcamentoAprovado()));

        assertThatThrownBy(() -> useCase.decidir(new DecisaoOrcamentoCommand("orc-1", true, null, null)))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("já foi decidido");
    }

    @Test
    void clienteNaoMexeEmOrcamentoDeOutro() {
        when(repository.buscarPorId("orc-1")).thenReturn(Optional.of(Fixtures.orcamentoAguardando()));

        assertThatThrownBy(() -> useCase.decidir(new DecisaoOrcamentoCommand("orc-1", true, null, "11144477735")))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> useCase.buscar("orc-1", "11144477735"))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void buscarInexistente() {
        when(repository.buscarPorId("x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.buscar("x", null)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void funcionarioBuscaQualquerOrcamento() {
        when(repository.buscarPorId("orc-1")).thenReturn(Optional.of(Fixtures.orcamentoAguardando()));
        when(repository.buscarPorOrdemServico(10L)).thenReturn(List.of(Fixtures.orcamentoAguardando()));

        assertThat(useCase.buscar("orc-1", null).getId()).isEqualTo("orc-1");
        assertThat(useCase.listarPorOrdemServico(10L)).hasSize(1);
    }

    @Test
    void cancelarPorSaga() {
        Orcamento o = Fixtures.orcamentoAprovado();
        when(repository.buscarPorSagaId("saga-1")).thenReturn(Optional.of(o));

        useCase.cancelarPorSaga("saga-1", 10L, "Estoque insuficiente");

        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
        verify(repository).salvar(o);
        verify(eventos).publicar(eq(ORCAMENTO_CANCELADO), eq("saga-1"), eq(10L), anyMap());
    }

    @Test
    void cancelarDuasVezesNaoQuebra() {
        Orcamento o = Fixtures.orcamentoAguardando();
        o.cancelar("primeira");
        when(repository.buscarPorSagaId("saga-1")).thenReturn(Optional.of(o));

        useCase.cancelarPorSaga("saga-1", 10L, "segunda");

        verify(repository, never()).salvar(any());
        verify(eventos).publicar(eq(ORCAMENTO_CANCELADO), eq("saga-1"), eq(10L), anyMap());
    }

    @Test
    void cancelarSagaSemOrcamentoSoConfirma() {
        when(repository.buscarPorSagaId("saga-x")).thenReturn(Optional.empty());

        useCase.cancelarPorSaga("saga-x", 3L, "timeout");

        verify(eventos).publicar(ORCAMENTO_CANCELADO, "saga-x", 3L, Map.of("motivo", "timeout"));
    }
}
