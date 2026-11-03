package com.fabrodiego.vpsmonitor.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.AppKind;

class HostGroupTest {

    @Test
    void servicoSystemdViraNomeDoServico() {
        HostGroup g = HostGroup.parse("[/../../../../system.slice/system-postgresql.slice/postgresql@16-main.service]|postgres");
        assertEquals(AppKind.SERVICO, g.kind());
        assertEquals("postgresql@16-main", g.name());
        assertEquals("postgres", g.command());
    }

    @Test
    void processoDePodNaoContaDeNovo() {
        assertNull(HostGroup.parse("[/../../../../kubepods.slice/kubepods-burstable.slice/x.scope]|java").kind());
        assertNull(HostGroup.parse("[/]|process-exporte").kind());
    }

    @Test
    void processoEmSessaoFicaSemNome() {
        HostGroup g = HostGroup.parse("[/../../../../user.slice/user-1000.slice/session-927.scope]|java");
        assertEquals(AppKind.SEM_NOME, g.kind());
        assertEquals("java", g.name());
        assertEquals("sessao 927 do usuario uid 1000", g.detail());
    }

    @Test
    void systemdDoUsuarioESistema() {
        HostGroup g = HostGroup.parse("[/../../../../user.slice/user-1002.slice/user@1002.service/init.scope]|systemd");
        assertEquals(AppKind.SISTEMA, g.kind());
        assertEquals("systemd do usuario 1002", g.name());
        assertEquals(AppKind.SISTEMA, HostGroup.parse("[/../../../../init.scope]|systemd").kind());
    }

    @Test
    void aceitaFormatoComPrefixoDeHierarquia() {
        HostGroup g = HostGroup.parse("[0::/system.slice/k3s.service]|k3s-server");
        assertEquals(AppKind.SERVICO, g.kind());
        assertEquals("k3s", g.name());
    }
}
