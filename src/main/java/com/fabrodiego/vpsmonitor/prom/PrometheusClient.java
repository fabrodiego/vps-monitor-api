package com.fabrodiego.vpsmonitor.prom;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fabrodiego.vpsmonitor.config.AppProperties;
import com.fabrodiego.vpsmonitor.prom.PromDtos.AlertsData;
import com.fabrodiego.vpsmonitor.prom.PromDtos.RawAlert;
import com.fabrodiego.vpsmonitor.prom.PromDtos.RawResult;
import com.fabrodiego.vpsmonitor.prom.PromDtos.Response;
import com.fabrodiego.vpsmonitor.prom.PromDtos.Sample;
import com.fabrodiego.vpsmonitor.prom.PromDtos.Series;
import com.fabrodiego.vpsmonitor.prom.PromDtos.VectorData;

/**
 * Le o Prometheus. As consultas (PromQL) sao fixas no codigo: nada vindo do navegador entra numa query.
 */
@Component
public class PrometheusClient {

    private static final ParameterizedTypeReference<Response<VectorData>> VECTOR = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<Response<AlertsData>> ALERTS = new ParameterizedTypeReference<>() {
    };

    private final RestClient http;

    public PrometheusClient(AppProperties props) {
        HttpClient jdk = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().baseUrl(props.prometheus().url()).requestFactory(factory).build();
    }

    /** Consulta instantanea: uma amostra por serie. */
    public List<Sample> query(String promql) {
        VectorData data = call(() -> http.get()
            .uri(u -> u.path("/api/v1/query").queryParam("query", "{q}").build(Map.of("q", promql)))
            .retrieve().body(VECTOR));
        List<Sample> out = new ArrayList<>();
        for (RawResult r : data.result()) {
            double v = parse(r.value().get(1));
            if (!Double.isNaN(v)) {
                out.add(new Sample(r.metric(), v));
            }
        }
        return out;
    }

    /** Primeiro valor da consulta (para consultas que devolvem um numero so). */
    public OptionalDouble scalar(String promql) {
        List<Sample> samples = query(promql);
        return samples.isEmpty() ? OptionalDouble.empty() : OptionalDouble.of(samples.get(0).value());
    }

    public List<Series> range(String promql, Instant start, Instant end, Duration step) {
        VectorData data = call(() -> http.get()
            .uri(u -> u.path("/api/v1/query_range")
                .queryParam("query", "{q}")
                .queryParam("start", start.getEpochSecond())
                .queryParam("end", end.getEpochSecond())
                .queryParam("step", step.toSeconds())
                .build(Map.of("q", promql)))
            .retrieve().body(VECTOR));
        List<Series> out = new ArrayList<>();
        for (RawResult r : data.result()) {
            List<double[]> points = new ArrayList<>(r.values().size());
            for (List<Object> pair : r.values()) {
                double v = parse(pair.get(1));
                if (!Double.isNaN(v)) {
                    points.add(new double[] {((Number) pair.get(0)).doubleValue(), v});
                }
            }
            out.add(new Series(r.metric(), points));
        }
        return out;
    }

    public List<RawAlert> alerts() {
        return call(() -> http.get().uri("/api/v1/alerts").retrieve().body(ALERTS)).alerts();
    }

    private static <T> T call(java.util.function.Supplier<Response<T>> request) {
        Response<T> response;
        try {
            response = request.get();
        } catch (RestClientException e) {
            throw new PrometheusException(e.getMessage(), e);
        }
        if (response == null || !"success".equals(response.status()) || response.data() == null) {
            throw new PrometheusException(response == null ? "resposta vazia" : response.error());
        }
        return response.data();
    }

    private static double parse(Object raw) {
        String s = String.valueOf(raw);
        return switch (s) {
            case "NaN", "+Inf", "-Inf" -> Double.NaN;
            default -> Double.parseDouble(s);
        };
    }
}
