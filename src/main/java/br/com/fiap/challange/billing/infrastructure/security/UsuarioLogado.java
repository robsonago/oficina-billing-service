package br.com.fiap.challange.billing.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class UsuarioLogado {

    private UsuarioLogado() {
    }

    /** Documento do cliente quando quem chama é o próprio cliente; nulo para funcionários. */
    public static String documentoSeCliente() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        boolean cliente = auth.getAuthorities().stream().anyMatch(a -> "ROLE_CLIENTE".equals(a.getAuthority()));
        return cliente ? auth.getName() : null;
    }
}
