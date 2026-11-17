package com.fabrodiego.vpsmonitor.monitor;

import java.util.List;

/** Resposta de /api/monitor/claude: sessoes do Claude Code abertas no host. */
public final class ClaudeDtos {

    private ClaudeDtos() {
    }

    /**
     * exporterUp: o Prometheus consegue ler o exportador do host.
     * remoteControlUp: a sessao usada pelo celular esta de pe.
     */
    public record ClaudeResponse(boolean exporterUp, boolean remoteControlUp, int sessionsOpen,
                                 Double memoryBytes, Double memoryHostShare, Double cpuCores,
                                 Tokens tokens24h, List<ClaudeSession> sessions) {
    }

    /**
     * contextUsage = contextTokens / contextLimit (estimativa: limite padrao configuravel; 1M se o modelo e [1m] ou o contexto passou do padrao).
     * tokens: total da sessao, incluindo subagentes; subagentTokens = tokens novos (sem cache lido) dos subagentes.
     */
    public record ClaudeSession(String id, String name, String status, String origin, String model,
                                Double memoryBytes, Double cpuCores, Integer processes, Long startedAt,
                                Integer contextTokens, Integer contextLimit, Double contextUsage,
                                Tokens tokens, Double subagentTokens, Integer turns) {
    }

    /**
     * cacheRead = contexto reaproveitado (barato); cacheWrite = contexto novo gravado no cache.
     * fresh = input + output + cacheWrite (tokens novos, que refletem custo); total inclui cacheRead.
     */
    public record Tokens(double input, double output, double cacheRead, double cacheWrite, double fresh, double total) {

        static Tokens of(double input, double output, double cacheRead, double cacheWrite) {
            return new Tokens(input, output, cacheRead, cacheWrite, input + output + cacheWrite,
                input + output + cacheRead + cacheWrite);
        }

        static final Tokens ZERO = of(0, 0, 0, 0);
    }
}
