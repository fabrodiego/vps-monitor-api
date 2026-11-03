package com.fabrodiego.vpsmonitor.prom;

import java.util.List;
import java.util.Map;

/** Formato da API HTTP do Prometheus (/api/v1/query, /query_range, /alerts). */
public final class PromDtos {

    private PromDtos() {
    }

    public record Response<T>(String status, T data, String error) {
    }

    public record VectorData(String resultType, List<RawResult> result) {
    }

    /** value = [timestamp, "valor"] (instantaneo); values = lista disso (range). */
    public record RawResult(Map<String, String> metric, List<Object> value, List<List<Object>> values) {
    }

    public record AlertsData(List<RawAlert> alerts) {
    }

    public record RawAlert(Map<String, String> labels, Map<String, String> annotations, String state,
                           String activeAt, String value) {
    }

    /** Uma serie ja convertida: labels + valor atual. */
    public record Sample(Map<String, String> labels, double value) {

        public String label(String name) {
            return labels.getOrDefault(name, "");
        }
    }

    /** Uma serie no tempo: pares [epoch segundos, valor]. */
    public record Series(Map<String, String> labels, List<double[]> points) {
    }
}
