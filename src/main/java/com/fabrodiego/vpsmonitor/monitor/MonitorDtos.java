package com.fabrodiego.vpsmonitor.monitor;

import java.util.List;

/** Valores ausentes (exporter fora, sensor inexistente) vem como null. Fracoes vao de 0 a 1. */
public final class MonitorDtos {

    private MonitorDtos() {
    }

    // ---------- visao geral ----------

    public record Overview(Cpu cpu, Memory memory, Disk disk, Gpu gpu, Network network, Counts counts,
                           Double uptimeSeconds) {
    }

    public record Cpu(Double usage, Integer cores, Double load1, Double load5, Double load15, Double tempCelsius) {
    }

    public record Memory(Double totalBytes, Double usedBytes, Double usage, Double swapTotalBytes, Double swapUsedBytes) {
    }

    public record Disk(String mountpoint, Double totalBytes, Double usedBytes, Double usage) {
    }

    public record Gpu(Double usage, Double vramUsedBytes, Double vramTotalBytes, Double tempCelsius) {
    }

    public record Network(Double rxBytesPerSec, Double txBytesPerSec) {
    }

    public record Counts(Integer podsRunning, Integer podsTotal, Integer sitesUp, Integer sitesTotal,
                         Integer alertsFiring, Integer unnamedApps) {
    }

    // ---------- series no tempo ----------

    public record SeriesResponse(String metric, String range, long stepSeconds, String unit, List<Line> lines) {
    }

    /** points: [epoch segundos, valor]. */
    public record Line(String name, List<double[]> points) {
    }

    // ---------- apps ----------

    public enum AppKind { POD, SERVICO, SISTEMA, SEM_NOME }

    /**
     * Um "app" que consome a maquina: um namespace do k3s, um servico systemd ou um processo solto.
     * warning=true quando nao da para saber de quem e (processo em sessao de usuario).
     */
    public record App(String name, AppKind kind, String detail, Double cpuCores, Double memoryBytes,
                      Integer processes, List<String> commands, boolean warning) {
    }

    public record AppsResponse(Double memoryTotalBytes, Integer cpuCores, List<App> apps) {
    }

    // ---------- pods ----------

    public record Pod(String namespace, String name, String phase, boolean ready, String waitingReason,
                      int restarts, Double cpuCores, Double memoryBytes, Double memoryLimitBytes,
                      Double startedAt) {
    }

    public record PodsResponse(List<Pod> pods) {
    }

    // ---------- sites ----------

    public record Site(String url, boolean up, Integer httpStatus, Double durationSeconds,
                       Double certExpiresInDays, Double uptime24h, String module) {
    }

    // ---------- banco ----------

    public record DatabaseResponse(boolean up, Integer maxConnections,
                                   List<Database> databases) {
    }

    public record Database(String name, Double sizeBytes, Integer connections) {
    }

    // ---------- alertas ----------

    public record Alert(String name, String state, String severity, String summary, String description,
                        String activeAt, String namespace, String target) {
    }
}
