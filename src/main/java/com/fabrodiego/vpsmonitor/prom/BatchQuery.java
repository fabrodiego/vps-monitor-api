package com.fabrodiego.vpsmonitor.prom;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;

import com.fabrodiego.vpsmonitor.prom.PromDtos.Sample;

/** Roda varias consultas ao mesmo tempo (threads virtuais) e guarda o resultado por nome. */
public final class BatchQuery {

    private final Map<String, List<Sample>> results;

    private BatchQuery(Map<String, List<Sample>> results) {
        this.results = results;
    }

    public static BatchQuery run(PrometheusClient prom, Map<String, String> queries) {
        Map<String, Future<List<Sample>>> futures = new LinkedHashMap<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            queries.forEach((name, promql) -> futures.put(name, executor.submit(() -> prom.query(promql))));
            Map<String, List<Sample>> results = new LinkedHashMap<>();
            for (var entry : futures.entrySet()) {
                results.put(entry.getKey(), entry.getValue().get());
            }
            return new BatchQuery(results);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PrometheusException("consulta interrompida", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof PrometheusException pe) {
                throw pe;
            }
            throw new CompletionException(e.getCause());
        }
    }

    public List<Sample> samples(String name) {
        return results.getOrDefault(name, List.of());
    }

    /** Valor da primeira serie, ou null se a consulta nao trouxe nada. */
    public Double value(String name) {
        List<Sample> s = samples(name);
        return s.isEmpty() ? null : s.get(0).value();
    }

    public OptionalDouble optional(String name) {
        Double v = value(name);
        return v == null ? OptionalDouble.empty() : OptionalDouble.of(v);
    }
}
