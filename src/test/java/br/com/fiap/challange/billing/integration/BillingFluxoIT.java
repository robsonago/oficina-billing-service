package br.com.fiap.challange.billing.integration;

import br.com.fiap.challange.billing.Fixtures;
import br.com.fiap.challange.billing.infrastructure.messaging.CanalEmMemoria;
import br.com.fiap.challange.billing.infrastructure.messaging.Mensagem;
import br.com.fiap.challange.billing.infrastructure.messaging.ReceptorMensagens;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@ActiveProfiles("test")
@Testcontainers
class BillingFluxoIT {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired
    MockMvc mvc;
    @Autowired
    ReceptorMensagens receptor;
    @Autowired
    CanalEmMemoria canal;
    @Autowired
    MongoTemplate mongoTemplate;
    @Autowired
    ObjectMapper mapper;

    @BeforeEach
    void limpar() {
        canal.limpar();
        mongoTemplate.getDb().drop();
    }

    private Mensagem gerarOrcamento(String sagaId, long osId) {
        JsonNode dados = mapper.valueToTree(Map.of(
                "numeroOS", "OS-2026-" + osId,
                "cliente", Map.of("nome", "Maria", "documento", Fixtures.DOCUMENTO, "email", "maria@email.com"),
                "itens", Fixtures.itens()));
        return Mensagem.nova("GERAR_ORCAMENTO", sagaId, osId, dados);
    }

    private String idDoOrcamentoGerado() {
        return canal.doTipo("ORCAMENTO_GERADO").getLast().dados().path("orcamentoId").asText();
    }

    @Test
    void caminhoFelizAteOPagamentoEEstornoNaCompensacao() throws Exception {
        Mensagem comando = gerarOrcamento("saga-ok", 10L);
        receptor.receber(comando);
        receptor.receber(comando); // reentrega do Pub/Sub: não pode duplicar

        assertThat(canal.doTipo("ORCAMENTO_GERADO")).hasSize(1);
        assertThat(canal.doTipo("ORCAMENTO_DISPONIVEL")).hasSize(1);
        String orcamentoId = idDoOrcamentoGerado();

        mvc.perform(get("/api/orcamentos/ordem-servico/10").header("Authorization", Tokens.funcionario()))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$[0].valorTotal").value(151.0))
                .andExpect(jsonPath("$[0].itens.length()").value(2));

        mvc.perform(post("/api/orcamentos/{id}/aprovacao", orcamentoId)
                        .header("Authorization", Tokens.cliente(Fixtures.DOCUMENTO))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"aprovado\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADO"))
                .andExpect(jsonPath("$.pagamento.linkPagamento").isNotEmpty());
        assertThat(canal.doTipo("ORCAMENTO_APROVADO")).hasSize(1);

        mvc.perform(post("/api/pagamentos/simulacao/{id}", orcamentoId)
                        .header("Authorization", Tokens.funcionario())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"aprovado\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAGO"));
        assertThat(canal.doTipo("PAGAMENTO_APROVADO")).hasSize(1);

        receptor.receber(Mensagem.nova("ESTORNAR_PAGAMENTO", "saga-ok", 10L,
                mapper.valueToTree(Map.of("motivo", "Falha na execução"))));

        mvc.perform(get("/api/orcamentos/{id}", orcamentoId).header("Authorization", Tokens.funcionario()))
                .andExpect(jsonPath("$.status").value("ESTORNADO"))
                .andExpect(jsonPath("$.pagamento.status").value("ESTORNADO"));
        assertThat(canal.doTipo("PAGAMENTO_ESTORNADO")).hasSize(1);
    }

    @Test
    void pagamentoRecusadoECancelamento() throws Exception {
        receptor.receber(gerarOrcamento("saga-recusa", 20L));
        String orcamentoId = idDoOrcamentoGerado();

        mvc.perform(post("/api/orcamentos/{id}/aprovacao", orcamentoId).header("Authorization", Tokens.funcionario())
                .contentType(MediaType.APPLICATION_JSON).content("{\"aprovado\":true}")).andExpect(status().isOk());
        mvc.perform(post("/api/pagamentos/simulacao/{id}", orcamentoId).header("Authorization", Tokens.funcionario())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"aprovado\":false}"))
                .andExpect(jsonPath("$.status").value("PAGAMENTO_RECUSADO"));
        assertThat(canal.doTipo("PAGAMENTO_RECUSADO")).hasSize(1);

        receptor.receber(Mensagem.nova("CANCELAR_ORCAMENTO", "saga-recusa", 20L,
                mapper.valueToTree(Map.of("motivo", "Pagamento recusado"))));
        mvc.perform(get("/api/orcamentos/{id}", orcamentoId).header("Authorization", Tokens.funcionario()))
                .andExpect(jsonPath("$.status").value("CANCELADO"));
    }

    @Test
    void rejeicaoERegrasDeAcesso() throws Exception {
        receptor.receber(gerarOrcamento("saga-rejeita", 30L));
        String orcamentoId = idDoOrcamentoGerado();

        mvc.perform(get("/api/orcamentos/{id}", orcamentoId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orcamentos/{id}", orcamentoId).header("Authorization", "Bearer token-falso"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orcamentos/{id}", orcamentoId).header("Authorization", Tokens.cliente("11144477735")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/orcamentos/ordem-servico/30").header("Authorization", Tokens.cliente("11144477735")))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/orcamentos/nao-existe").header("Authorization", Tokens.funcionario()))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/orcamentos/{id}/aprovacao", orcamentoId).header("Authorization", Tokens.funcionario())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/orcamentos/{id}/aprovacao", orcamentoId).header("Authorization", Tokens.funcionario())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"aprovado\":false,\"observacao\":\"Caro\"}"))
                .andExpect(jsonPath("$.status").value("REJEITADO"));
        mvc.perform(post("/api/orcamentos/{id}/aprovacao", orcamentoId).header("Authorization", Tokens.funcionario())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"aprovado\":true}"))
                .andExpect(status().isUnprocessableEntity());
        assertThat(canal.doTipo("ORCAMENTO_REJEITADO")).hasSize(1);
    }

    @Test
    void webhookPublicoIgnoraOQueNaoEPagamento() throws Exception {
        mvc.perform(post("/api/pagamentos/webhook").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"merchant_order\",\"data\":{\"id\":\"1\"}}"))
                .andExpect(status().isOk());

        receptor.receber(gerarOrcamento("saga-webhook", 40L));
        String orcamentoId = idDoOrcamentoGerado();
        mvc.perform(post("/api/orcamentos/{id}/aprovacao", orcamentoId).header("Authorization", Tokens.funcionario())
                .contentType(MediaType.APPLICATION_JSON).content("{\"aprovado\":true}")).andExpect(status().isOk());

        // Pagamento desconhecido pelo gateway: o webhook devolve erro para o MP tentar de novo depois
        mvc.perform(post("/api/pagamentos/webhook").param("type", "payment").param("data.id", "999"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void osSemItensRespondeFalha() {
        receptor.receber(Mensagem.nova("GERAR_ORCAMENTO", "saga-vazia", 50L, mapper.valueToTree(Map.of(
                "numeroOS", "OS-50", "cliente", Map.of("nome", "X"), "itens", java.util.List.of()))));

        assertThat(canal.doTipo("ORCAMENTO_FALHOU")).hasSize(1);
    }

    @Test
    void actuatorExpoeHealthEPrometheus() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
        mvc.perform(get("/billing/api-docs")).andExpect(status().isOk());
    }
}
