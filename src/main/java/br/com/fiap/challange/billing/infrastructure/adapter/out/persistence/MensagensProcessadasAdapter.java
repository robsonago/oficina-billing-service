package br.com.fiap.challange.billing.infrastructure.adapter.out.persistence;

import br.com.fiap.challange.billing.domain.port.out.MensagensProcessadasPort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
class MensagensProcessadasAdapter implements MensagensProcessadasPort {

    private final MongoTemplate mongoTemplate;

    @Override
    public boolean jaProcessada(String mensagemId) {
        return mongoTemplate.findById(mensagemId, MensagemProcessadaDocument.class) != null;
    }

    @Override
    public void registrar(String mensagemId, String tipo) {
        try {
            mongoTemplate.insert(new MensagemProcessadaDocument(mensagemId, tipo, Instant.now()));
        } catch (DuplicateKeyException e) {
            // outra réplica terminou de processar a mesma mensagem antes; tudo certo
        }
    }
}
