package br.com.fiap.challange.billing.infrastructure.adapter.out.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "mensagens_processadas")
public record MensagemProcessadaDocument(@Id String id, String tipo,
                                         @Indexed(expireAfter = "30d") Instant processadaEm) {
}
