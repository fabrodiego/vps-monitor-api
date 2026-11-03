package com.fabrodiego.vpsmonitor.monitor;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Graficos disponiveis. Cada um tem uma ou mais linhas (nome -> PromQL).
 * "$w" vira a janela do rate, que acompanha o passo do grafico.
 */
public enum SeriesMetric {

    CPU("fraction", Map.of("CPU", "1 - avg(rate(node_cpu_seconds_total{mode=\"idle\"}[$w]))")),
    RAM("fraction", Map.of("RAM", "1 - node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes")),
    DISK("fraction", Map.of("Disco /",
        "1 - node_filesystem_avail_bytes{mountpoint=\"/\"} / node_filesystem_size_bytes{mountpoint=\"/\"}")),
    GPU("fraction", ordered(
        "GPU", "max(avg_over_time(node_drm_gpu_busy_percent[$w])) / 100",
        "VRAM", "max(node_drm_memory_vram_used_bytes) / max(node_drm_memory_vram_size_bytes)")),
    TEMP("celsius", ordered(
        "CPU", "max(node_hwmon_temp_celsius * on(chip) group_left node_hwmon_chip_names{chip_name=\"coretemp\"})",
        "GPU", "max(node_hwmon_temp_celsius * on(chip) group_left node_hwmon_chip_names{chip_name=\"amdgpu\"})")),
    NET("bytes_per_sec", ordered(
        "Entrada", "sum(rate(node_network_receive_bytes_total{" + Queries.NET_DEVICES + "}[$w]))",
        "Saida", "sum(rate(node_network_transmit_bytes_total{" + Queries.NET_DEVICES + "}[$w]))"));

    private final String unit;
    private final Map<String, String> lines;

    SeriesMetric(String unit, Map<String, String> lines) {
        this.unit = unit;
        this.lines = lines;
    }

    public String unit() {
        return unit;
    }

    public Map<String, String> lines(Duration step) {
        String window = Math.max(120, step.toSeconds() * 2) + "s";
        Map<String, String> out = new LinkedHashMap<>();
        lines.forEach((name, q) -> out.put(name, q.replace("$w", window)));
        return out;
    }

    private static Map<String, String> ordered(String... pairs) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put(pairs[i], pairs[i + 1]);
        }
        return m;
    }

    /** Periodos do grafico e o passo de cada um (~240-360 pontos). */
    public enum Range {
        H1(Duration.ofHours(1), Duration.ofSeconds(15)),
        H6(Duration.ofHours(6), Duration.ofMinutes(1)),
        H24(Duration.ofHours(24), Duration.ofMinutes(5)),
        D7(Duration.ofDays(7), Duration.ofMinutes(30));

        public final Duration length;
        public final Duration step;

        Range(Duration length, Duration step) {
            this.length = length;
            this.step = step;
        }

        public static Range parse(String value) {
            return switch (value) {
                case "1h" -> H1;
                case "6h" -> H6;
                case "24h" -> H24;
                case "7d" -> D7;
                default -> throw new IllegalArgumentException("range deve ser 1h, 6h, 24h ou 7d");
            };
        }

        public String label() {
            return switch (this) {
                case H1 -> "1h";
                case H6 -> "6h";
                case H24 -> "24h";
                case D7 -> "7d";
            };
        }
    }
}
