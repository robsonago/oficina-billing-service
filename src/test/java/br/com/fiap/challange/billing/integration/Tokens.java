package br.com.fiap.challange.billing.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.util.Base64;
import java.util.Date;
import java.util.Map;

/** Gera tokens como os emitidos pelo OS Service (funcionário) e pela function de CPF (cliente). */
final class Tokens {

    static final String SEGREDO = "dGVzdFNlY3JldEtleUZvclRlc3RpbmdQdXJwb3Nlc09maWNpbmExMjM0NQ==";

    private Tokens() {
    }

    static String funcionario() {
        return gerar("admin", "ADMIN");
    }

    static String cliente(String documento) {
        return gerar(documento, "CLIENTE");
    }

    private static String gerar(String subject, String role) {
        return "Bearer " + Jwts.builder()
                .claims(Map.of("role", role))
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SEGREDO)))
                .compact();
    }
}
