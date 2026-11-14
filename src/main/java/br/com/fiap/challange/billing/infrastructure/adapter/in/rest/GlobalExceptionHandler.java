package br.com.fiap.challange.billing.infrastructure.adapter.in.rest;

import br.com.fiap.challange.billing.domain.exception.AcessoNegadoException;
import br.com.fiap.challange.billing.domain.exception.FalhaGatewayPagamentoException;
import br.com.fiap.challange.billing.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.challange.billing.domain.exception.RegraDeNegocioException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErroResponse(String mensagem, int status, LocalDateTime timestamp, Map<String, String> campos) {
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResponse> naoEncontrado(RecursoNaoEncontradoException ex) {
        return erro(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<ErroResponse> regra(RegraDeNegocioException ex) {
        log.warn("Regra de negócio: {}", ex.getMessage());
        return erro(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<ErroResponse> acessoNegado(AcessoNegadoException ex) {
        return erro(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(FalhaGatewayPagamentoException.class)
    public ResponseEntity<ErroResponse> gateway(FalhaGatewayPagamentoException ex) {
        log.error("Falha no gateway de pagamento: {}", ex.getMessage());
        return erro(HttpStatus.BAD_GATEWAY, "Não foi possível falar com o gateway de pagamento agora, tente novamente");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> validacao(MethodArgumentNotValidException ex) {
        Map<String, String> campos = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(e ->
                campos.put(e instanceof FieldError fe ? fe.getField() : e.getObjectName(), e.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(new ErroResponse("Dados inválidos", 400, LocalDateTime.now(), campos));
    }

    private ResponseEntity<ErroResponse> erro(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(new ErroResponse(mensagem, status.value(), LocalDateTime.now(), null));
    }
}
