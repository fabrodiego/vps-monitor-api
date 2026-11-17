package com.fabrodiego.vpsmonitor.monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fabrodiego.vpsmonitor.monitor.ClaudeDtos.ClaudeResponse;
import com.fabrodiego.vpsmonitor.monitor.ClaudeDtos.ClaudeSession;
import com.fabrodiego.vpsmonitor.prom.PromDtos.Sample;

class ClaudeServiceTest {

    private static Sample s(double v, String... kv) {
        Map<String, String> labels = new java.util.HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            labels.put(kv[i], kv[i + 1]);
        }
        return new Sample(labels, v);
    }

    @Test
    void montaSessoesComTokensEContexto() {
        Map<String, List<Sample>> data = Map.of(
            "exporterUp", List.of(s(1)),
            "rcUp", List.of(s(1)),
            "open", List.of(s(2)),
            "info", List.of(
                s(1, "session_id", "a", "name", "Projeto X", "status", "busy", "origem", "celular", "model", "modelo-1"),
                s(1, "session_id", "b", "name", "Outra", "status", "idle", "origem", "cli", "model", "modelo-2[1m]")),
            "mem", List.of(s(400, "session_id", "a"), s(100, "session_id", "b")),
            "started", List.of(s(100, "session_id", "a"), s(200, "session_id", "b")),
            "ctx", List.of(s(50_000, "session_id", "a"), s(250_000, "session_id", "b")),
            "tok", List.of(s(10, "session_id", "a", "type", "input"), s(90, "session_id", "a", "type", "cache_read")),
            "sub", List.of(s(30, "session_id", "a")),
            "memTotal", List.of(s(1000)));

        ClaudeResponse r = ClaudeService.build(k -> data.getOrDefault(k, List.of()));

        assertTrue(r.exporterUp());
        assertTrue(r.remoteControlUp());
        assertEquals(2, r.sessionsOpen());
        assertEquals(500, r.memoryBytes());
        assertEquals(0.5, r.memoryHostShare());

        // mais recente primeiro
        assertEquals("b", r.sessions().get(0).id());
        ClaudeSession b = r.sessions().get(0);
        assertEquals(1_000_000, b.contextLimit());
        assertEquals(0.25, b.contextUsage());

        ClaudeSession a = r.sessions().get(1);
        assertEquals("Projeto X", a.name());
        assertEquals(200_000, a.contextLimit());
        assertEquals(0.25, a.contextUsage());
        assertEquals(100, a.tokens().total());
        assertEquals(30, a.subagentTokens());
    }

    @Test
    void semExportadorDevolveVazioEFora() {
        ClaudeResponse r = ClaudeService.build(k -> List.of());
        assertFalse(r.exporterUp());
        assertFalse(r.remoteControlUp());
        assertEquals(0, r.sessionsOpen());
        assertNull(r.memoryHostShare());
        assertTrue(r.sessions().isEmpty());
        assertEquals(0, r.tokens24h().total());
    }
}
