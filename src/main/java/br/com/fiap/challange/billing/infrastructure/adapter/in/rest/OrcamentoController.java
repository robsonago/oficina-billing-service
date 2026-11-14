package br.com.fiap.challange.billing.infrastructure.adapter.in.rest;

import br.com.fiap.challange.billing.domain.port.in.OrcamentoInputPort;
import br.com.fiap.challange.billing.domain.port.in.command.DecisaoOrcamentoCommand;
import br.com.fiap.challange.billing.infrastructure.adapter.in.rest.dto.DecisaoOrcamentoRequest;
import br.com.fiap.challange.billing.infrastructure.adapter.in.rest.dto.OrcamentoResponse;
import br.com.fiap.challange.billing.infrastructure.security.UsuarioLogado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orcamentos")
@RequiredArgsConstructor
@Tag(name = "Orçamentos", description = "Consulta e aprovação dos orçamentos gerados pela saga da OS")
public class OrcamentoController {

    private final OrcamentoInputPort orcamentos;

    @GetMapping("/{id}")
    @Operation(summary = "Buscar orçamento (o cliente só enxerga os próprios)")
    public ResponseEntity<OrcamentoResponse> buscar(@PathVariable String id) {
        return ResponseEntity.ok(OrcamentoResponse.from(orcamentos.buscar(id, UsuarioLogado.documentoSeCliente())));
    }

    @GetMapping("/ordem-servico/{ordemServicoId}")
    @Operation(summary = "Orçamentos de uma OS, do mais recente para o mais antigo")
    public ResponseEntity<List<OrcamentoResponse>> porOrdemServico(@PathVariable Long ordemServicoId) {
        String documento = UsuarioLogado.documentoSeCliente();
        return ResponseEntity.ok(orcamentos.listarPorOrdemServico(ordemServicoId).stream()
                .filter(o -> documento == null || documento.equals(o.getCliente().documento()))
                .map(OrcamentoResponse::from).toList());
    }

    @PostMapping("/{id}/aprovacao")
    @Operation(summary = "Aprovar ou rejeitar o orçamento. Aprovado: cria a cobrança no Mercado Pago e devolve o link de pagamento")
    public ResponseEntity<OrcamentoResponse> decidir(@PathVariable String id,
                                                     @Valid @RequestBody DecisaoOrcamentoRequest request) {
        var command = new DecisaoOrcamentoCommand(id, request.aprovado(), request.observacao(),
                UsuarioLogado.documentoSeCliente());
        return ResponseEntity.ok(OrcamentoResponse.from(orcamentos.decidir(command)));
    }
}
