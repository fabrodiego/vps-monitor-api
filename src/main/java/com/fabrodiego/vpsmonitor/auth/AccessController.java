package com.fabrodiego.vpsmonitor.auth;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fabrodiego.vpsmonitor.config.AppProperties;

/**
 * O cookie do Cloudflare Access vale por dominio. Na primeira visita o navegador ainda nao tem o
 * cookie do dominio da API e as chamadas do site falham. O site entao navega para esta rota:
 * o Access emite o cookie (sem pedir login de novo) e aqui devolvemos o usuario para o site.
 * O destino e fixo (a primeira origem do CORS), entao nao serve de redirecionamento aberto.
 */
@RestController
public class AccessController {

    private final URI site;

    public AccessController(AppProperties props) {
        this.site = URI.create(props.cors().origins().get(0) + "/");
    }

    @GetMapping("/api/access/voltar")
    public ResponseEntity<Void> back() {
        return ResponseEntity.status(HttpStatus.FOUND).location(site).build();
    }
}
