package com.fabrodiego.vpsmonitor.monitor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Service;

import com.fabrodiego.vpsmonitor.config.AppProperties;
import com.fabrodiego.vpsmonitor.monitor.ClaudeDtos.ClaudeResponse;
import com.fabrodiego.vpsmonitor.monitor.ClaudeDtos.ClaudeSession;
import com.fabrodiego.vpsmonitor.monitor.ClaudeDtos.Tokens;
import com.fabrodiego.vpsmonitor.prom.BatchQuery;
import com.fabrodiego.vpsmonitor.prom.PromDtos.Sample;
import com.fabrodiego.vpsmonitor.prom.PrometheusClient;

/** Sessoes do Claude Code: le as metricas claude_* do exportador do host. */
@Service
public class ClaudeService {

    /** Janela grande: modelos com "[1m]" no nome, ou sessoes que ja passaram do padrao configurado. */
    static final int LARGE_CONTEXT_WINDOW = 1_000_000;

    static final Map<String, String> QUERIES = new LinkedHashMap<>();

    static {
        QUERIES.put("exporterUp", "max(up{job=\"claude-exporter\"})");
        QUERIES.put("rcUp", "max(claude_remote_control_up)");
        QUERIES.put("open", "max(claude_sessions_open)");
        QUERIES.put("info", "claude_session_info");
        QUERIES.put("mem", "claude_session_memory_bytes");
        QUERIES.put("cpu", "rate(claude_session_cpu_seconds_total[2m])");
        QUERIES.put("procs", "claude_session_processes");
        QUERIES.put("started", "claude_session_started_timestamp_seconds");
        QUERIES.put("ctx", "claude_session_context_tokens");
        QUERIES.put("turns", "claude_session_turns_total");
        QUERIES.put("tok", "sum by (session_id, type) (claude_session_tokens_total)");
        QUERIES.put("sub", "sum by (session_id) (claude_session_tokens_total{origem=\"subagentes\",type!=\"cache_read\"})");
        QUERIES.put("tok24h", "sum by (type) (increase(claude_session_tokens_total[24h]))");
        QUERIES.put("memTotal", Queries.MEM_TOTAL);
    }

    private final PrometheusClient prom;
    private final int defaultContextWindow;

    public ClaudeService(PrometheusClient prom, AppProperties props) {
        this.prom = prom;
        this.defaultContextWindow = props.claude().contextWindow();
    }

    public ClaudeResponse claude() {
        BatchQuery r = BatchQuery.run(prom, QUERIES);
        return build(r::samples, defaultContextWindow);
    }

    /** Monta a resposta a partir das series (separado para testar sem Prometheus). */
    static ClaudeResponse build(Function<String, List<Sample>> samples, int defaultContextWindow) {
        Map<String, Double> mem = bySession(samples.apply("mem"));
        Map<String, Double> cpu = bySession(samples.apply("cpu"));
        Map<String, Double> procs = bySession(samples.apply("procs"));
        Map<String, Double> started = bySession(samples.apply("started"));
        Map<String, Double> ctx = bySession(samples.apply("ctx"));
        Map<String, Double> turns = bySession(samples.apply("turns"));
        Map<String, Double> sub = bySession(samples.apply("sub"));
        Map<String, Map<String, Double>> tok = new HashMap<>();
        for (Sample s : samples.apply("tok")) {
            tok.computeIfAbsent(s.label("session_id"), k -> new HashMap<>()).merge(s.label("type"), s.value(), Double::sum);
        }

        List<ClaudeSession> sessions = new ArrayList<>();
        for (Sample s : samples.apply("info")) {
            String id = s.label("session_id");
            String model = s.label("model");
            Integer context = toInt(ctx.get(id));
            int limit = contextWindow(model, context, defaultContextWindow);
            sessions.add(new ClaudeSession(id, s.label("name"), s.label("status"), s.label("origem"), model,
                mem.get(id), cpu.get(id), toInt(procs.get(id)),
                started.containsKey(id) ? Math.round(started.get(id)) : null,
                context, limit, context == null ? null : Math.min(1.0, context / (double) limit),
                tokens(tok.get(id)), sub.get(id), toInt(turns.get(id))));
        }
        sessions.sort(Comparator.comparing(ClaudeSession::startedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        Map<String, Double> t24 = new HashMap<>();
        samples.apply("tok24h").forEach(s -> t24.put(s.label("type"), s.value()));

        double memSum = sessions.stream().mapToDouble(x -> x.memoryBytes() == null ? 0 : x.memoryBytes()).sum();
        double cpuSum = sessions.stream().mapToDouble(x -> x.cpuCores() == null ? 0 : x.cpuCores()).sum();
        Double memTotal = first(samples.apply("memTotal"));
        Double open = first(samples.apply("open"));

        return new ClaudeResponse(
            isOne(first(samples.apply("exporterUp"))),
            isOne(first(samples.apply("rcUp"))),
            open == null ? sessions.size() : (int) Math.round(open),
            memSum, memTotal == null || memTotal == 0 ? null : memSum / memTotal, cpuSum,
            tokens(t24), sessions);
    }

    static int contextWindow(String model, Integer context, int defaultWindow) {
        boolean large = model != null && model.toLowerCase().contains("[1m]");
        boolean overDefault = context != null && context > defaultWindow;
        return large || overDefault ? LARGE_CONTEXT_WINDOW : defaultWindow;
    }

    private static Tokens tokens(Map<String, Double> byType) {
        if (byType == null) {
            return Tokens.ZERO;
        }
        return Tokens.of(byType.getOrDefault("input", 0.0), byType.getOrDefault("output", 0.0),
            byType.getOrDefault("cache_read", 0.0), byType.getOrDefault("cache_write", 0.0));
    }

    private static Map<String, Double> bySession(List<Sample> samples) {
        Map<String, Double> out = new HashMap<>();
        samples.forEach(s -> out.merge(s.label("session_id"), s.value(), Double::sum));
        return out;
    }

    private static Double first(List<Sample> s) {
        return s.isEmpty() ? null : s.get(0).value();
    }

    private static boolean isOne(Double v) {
        return v != null && v == 1;
    }

    private static Integer toInt(Double v) {
        return v == null ? null : (int) Math.round(v);
    }
}
