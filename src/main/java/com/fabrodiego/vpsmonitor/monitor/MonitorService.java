package com.fabrodiego.vpsmonitor.monitor;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.Alert;
import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.App;
import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.AppKind;
import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.AppsResponse;
import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.Counts;
import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.Cpu;
import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.Database;
import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.DatabaseResponse;
import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.Disk;
import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.Gpu;
import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.Line;
import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.Memory;
import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.Network;
import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.Overview;
import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.Pod;
import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.PodsResponse;
import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.SeriesResponse;
import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.Site;
import com.fabrodiego.vpsmonitor.prom.BatchQuery;
import com.fabrodiego.vpsmonitor.prom.PromDtos.RawAlert;
import com.fabrodiego.vpsmonitor.prom.PromDtos.Sample;
import com.fabrodiego.vpsmonitor.prom.PromDtos.Series;
import com.fabrodiego.vpsmonitor.prom.PrometheusClient;

import static com.fabrodiego.vpsmonitor.monitor.Queries.*;

@Service
public class MonitorService {

    /** Processo sem nome so vira aviso se pesar: 50 MiB de RAM ou 5% de um nucleo. */
    static final double UNNAMED_MEM_BYTES = 50 * 1024 * 1024;
    static final double UNNAMED_CPU_CORES = 0.05;
    private static final Set<String> HIDDEN_ALERTS = Set.of("Watchdog", "InfoInhibitor");

    private final PrometheusClient prom;
    private final Clock clock;

    public MonitorService(PrometheusClient prom, Clock clock) {
        this.prom = prom;
        this.clock = clock;
    }

    public Overview overview() {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("cpu", CPU_USAGE);
        q.put("cores", CPU_CORES);
        q.put("load1", LOAD1);
        q.put("load5", LOAD5);
        q.put("load15", LOAD15);
        q.put("cpuTemp", CPU_TEMP);
        q.put("memTotal", MEM_TOTAL);
        q.put("memAvail", MEM_AVAILABLE);
        q.put("swapTotal", SWAP_TOTAL);
        q.put("swapFree", SWAP_FREE);
        q.put("diskSize", DISK_SIZE);
        q.put("diskAvail", DISK_AVAIL);
        q.put("gpu", GPU_BUSY);
        q.put("vramUsed", VRAM_USED);
        q.put("vramTotal", VRAM_TOTAL);
        q.put("gpuTemp", GPU_TEMP);
        q.put("rx", NET_RX);
        q.put("tx", NET_TX);
        q.put("uptime", UPTIME);
        q.put("podsRunning", PODS_RUNNING);
        q.put("podsTotal", PODS_TOTAL);
        q.put("sitesUp", SITES_UP);
        q.put("sitesTotal", SITES_TOTAL);
        BatchQuery r = BatchQuery.run(prom, q);

        Double memTotal = r.value("memTotal");
        Double memUsed = minus(memTotal, r.value("memAvail"));
        Double swapTotal = r.value("swapTotal");
        Double diskSize = r.value("diskSize");
        Double diskUsed = minus(diskSize, r.value("diskAvail"));

        int firing = (int) visibleAlerts().stream().filter(a -> "firing".equals(a.state())).count();
        int unnamed = (int) apps().apps().stream().filter(App::warning).count();

        return new Overview(
            new Cpu(r.value("cpu"), toInt(r.value("cores")), r.value("load1"), r.value("load5"), r.value("load15"),
                r.value("cpuTemp")),
            new Memory(memTotal, memUsed, ratio(memUsed, memTotal), swapTotal, minus(swapTotal, r.value("swapFree"))),
            new Disk("/", diskSize, diskUsed, ratio(diskUsed, diskSize)),
            new Gpu(r.value("gpu"), r.value("vramUsed"), r.value("vramTotal"), r.value("gpuTemp")),
            new Network(r.value("rx"), r.value("tx")),
            new Counts(toInt(r.value("podsRunning")), toInt(r.value("podsTotal")), toInt(r.value("sitesUp")),
                toInt(r.value("sitesTotal")), firing, unnamed),
            r.value("uptime"));
    }

    public SeriesResponse series(SeriesMetric metric, SeriesMetric.Range range) {
        Instant end = clock.instant();
        Instant start = end.minus(range.length);
        List<Line> lines = new ArrayList<>();
        metric.lines(range.step).forEach((name, promql) -> {
            List<Series> result = prom.range(promql, start, end, range.step);
            lines.add(new Line(name, result.isEmpty() ? List.of() : result.get(0).points()));
        });
        return new SeriesResponse(metric.name().toLowerCase(), range.label(), range.step.toSeconds(), metric.unit(), lines);
    }

    public AppsResponse apps() {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("nsCpu", NS_CPU);
        q.put("nsMem", NS_MEM);
        q.put("nsPods", NS_PODS);
        q.put("procCpu", PROC_CPU);
        q.put("procMem", PROC_MEM);
        q.put("procCount", PROC_COUNT);
        q.put("memTotal", MEM_TOTAL);
        q.put("cores", CPU_CORES);
        BatchQuery r = BatchQuery.run(prom, q);

        List<App> apps = new ArrayList<>();

        // pods: um app por namespace
        Map<String, Double> nsCpu = byLabel(r.samples("nsCpu"), "namespace");
        Map<String, Double> nsMem = byLabel(r.samples("nsMem"), "namespace");
        Map<String, Double> nsPods = byLabel(r.samples("nsPods"), "namespace");
        Set<String> namespaces = new TreeSet<>(nsPods.keySet());
        namespaces.addAll(nsMem.keySet());
        for (String ns : namespaces) {
            int pods = nsPods.getOrDefault(ns, 0.0).intValue();
            apps.add(new App(ns, AppKind.POD, pods + (pods == 1 ? " pod" : " pods") + " no k3s",
                nsCpu.get(ns), nsMem.get(ns), null, List.of(), false));
        }

        // host: agrupa os processos do mesmo servico/sessao
        Map<String, Double> procCpu = byLabel(r.samples("procCpu"), "groupname");
        Map<String, Double> procMem = byLabel(r.samples("procMem"), "groupname");
        Map<String, Double> procCount = byLabel(r.samples("procCount"), "groupname");
        Map<String, HostAccumulator> host = new LinkedHashMap<>();
        for (String group : procMem.keySet()) {
            HostGroup g = HostGroup.parse(group);
            if (g.kind() == null) {
                continue;
            }
            host.computeIfAbsent(g.kind() + "|" + g.name() + "|" + g.detail(), k -> new HostAccumulator(g))
                .add(g.command(), procCpu.getOrDefault(group, 0.0), procMem.get(group),
                    procCount.getOrDefault(group, 0.0));
        }
        host.values().forEach(h -> apps.add(h.toApp()));

        apps.sort(Comparator.comparing((App a) -> a.memoryBytes() == null ? 0 : a.memoryBytes()).reversed());
        return new AppsResponse(r.value("memTotal"), toInt(r.value("cores")), apps);
    }

    public PodsResponse pods() {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("info", POD_INFO);
        q.put("phase", POD_PHASE);
        q.put("ready", POD_READY);
        q.put("waiting", POD_WAITING);
        q.put("restarts", POD_RESTARTS);
        q.put("cpu", POD_CPU);
        q.put("mem", POD_MEM);
        q.put("limit", POD_MEM_LIMIT);
        q.put("start", POD_START);
        BatchQuery r = BatchQuery.run(prom, q);

        Map<String, String> phase = labelByPod(r.samples("phase"), "phase");
        Map<String, String> waiting = labelByPod(r.samples("waiting"), "reason");
        Set<String> ready = new java.util.HashSet<>(labelByPod(r.samples("ready"), "pod").keySet());
        Map<String, Double> restarts = valueByPod(r.samples("restarts"));
        Map<String, Double> cpu = valueByPod(r.samples("cpu"));
        Map<String, Double> mem = valueByPod(r.samples("mem"));
        Map<String, Double> limit = valueByPod(r.samples("limit"));
        Map<String, Double> start = valueByPod(r.samples("start"));

        List<Pod> pods = new ArrayList<>();
        for (Sample s : r.samples("info")) {
            String key = podKey(s);
            pods.add(new Pod(s.label("namespace"), s.label("pod"), phase.getOrDefault(key, "Unknown"),
                ready.contains(key), waiting.get(key), restarts.getOrDefault(key, 0.0).intValue(),
                cpu.get(key), mem.get(key), limit.get(key), start.get(key)));
        }
        pods.sort(Comparator.comparing(Pod::namespace).thenComparing(Pod::name));
        return new PodsResponse(pods);
    }

    public List<Site> sites() {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("success", PROBE_SUCCESS);
        q.put("status", PROBE_STATUS);
        q.put("duration", PROBE_DURATION);
        q.put("cert", PROBE_CERT_DAYS);
        q.put("uptime", PROBE_UPTIME_24H);
        BatchQuery r = BatchQuery.run(prom, q);

        Map<String, Double> status = byLabel(r.samples("status"), "instance");
        Map<String, Double> duration = byLabel(r.samples("duration"), "instance");
        Map<String, Double> cert = byLabel(r.samples("cert"), "instance");
        Map<String, Double> uptime = byLabel(r.samples("uptime"), "instance");

        List<Site> sites = new ArrayList<>();
        for (Sample s : r.samples("success")) {
            String url = s.label("instance");
            String module = s.label("job").replace("probe/monitoramento/", "");
            Double code = status.get(url);
            sites.add(new Site(url, s.value() == 1, code == null || code == 0 ? null : code.intValue(),
                duration.get(url), cert.get(url), uptime.get(url), module));
        }
        sites.sort(Comparator.comparing(Site::url));
        return sites;
    }

    public DatabaseResponse database() {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("up", PG_UP);
        q.put("max", PG_MAX_CONN);
        q.put("size", PG_SIZE);
        q.put("conn", PG_CONN);
        BatchQuery r = BatchQuery.run(prom, q);

        Map<String, Double> conn = byLabel(r.samples("conn"), "datname");
        List<Database> dbs = new ArrayList<>();
        for (Sample s : r.samples("size")) {
            String name = s.label("datname");
            dbs.add(new Database(name, s.value(), conn.getOrDefault(name, 0.0).intValue()));
        }
        dbs.sort(Comparator.comparing(Database::sizeBytes).reversed());
        Double up = r.value("up");
        return new DatabaseResponse(up != null && up == 1, toInt(r.value("max")), dbs);
    }

    public List<Alert> alerts() {
        return visibleAlerts().stream()
            .map(a -> new Alert(a.labels().getOrDefault("alertname", "?"), a.state(),
                a.labels().getOrDefault("severity", "none"),
                annotation(a, "summary"), annotation(a, "description"), a.activeAt(),
                a.labels().get("namespace"), target(a.labels())))
            .sorted(Comparator.comparing((Alert a) -> !"firing".equals(a.state()))
                .thenComparing(a -> severityOrder(a.severity()))
                .thenComparing(Alert::name))
            .toList();
    }

    private List<RawAlert> visibleAlerts() {
        return prom.alerts().stream()
            .filter(a -> !HIDDEN_ALERTS.contains(a.labels().getOrDefault("alertname", "")))
            .toList();
    }

    // ---------- helpers ----------

    private static final class HostAccumulator {
        private final HostGroup group;
        private final Set<String> commands = new TreeSet<>();
        private double cpu;
        private double mem;
        private double procs;

        HostAccumulator(HostGroup group) {
            this.group = group;
        }

        void add(String command, double cpu, double mem, double procs) {
            if (!command.isBlank()) {
                commands.add(command);
            }
            this.cpu += cpu;
            this.mem += mem;
            this.procs += procs;
        }

        App toApp() {
            boolean warning = group.kind() == AppKind.SEM_NOME
                && (mem >= UNNAMED_MEM_BYTES || cpu >= UNNAMED_CPU_CORES);
            return new App(group.name(), group.kind(), group.detail(), cpu, mem, (int) procs,
                List.copyOf(commands), warning);
        }
    }

    private static String annotation(RawAlert a, String key) {
        return a.annotations() == null ? null : a.annotations().get(key);
    }

    private static String target(Map<String, String> labels) {
        for (String key : List.of("instance", "pod", "deployment", "daemonset", "statefulset", "job", "service")) {
            String v = labels.get(key);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static int severityOrder(String severity) {
        return switch (severity) {
            case "critical" -> 0;
            case "warning" -> 1;
            default -> 2;
        };
    }

    private static Map<String, Double> byLabel(List<Sample> samples, String label) {
        Map<String, Double> out = new HashMap<>();
        samples.forEach(s -> out.merge(s.label(label), s.value(), Double::sum));
        return out;
    }

    private static String podKey(Sample s) {
        return s.label("namespace") + "/" + s.label("pod");
    }

    private static Map<String, Double> valueByPod(List<Sample> samples) {
        Map<String, Double> out = new HashMap<>();
        samples.forEach(s -> out.merge(podKey(s), s.value(), Double::sum));
        return out;
    }

    private static Map<String, String> labelByPod(List<Sample> samples, String label) {
        Map<String, String> out = new HashMap<>();
        samples.forEach(s -> out.putIfAbsent(podKey(s), s.label(label)));
        return out;
    }

    private static Double minus(Double a, Double b) {
        return a == null || b == null ? null : a - b;
    }

    private static Double ratio(Double part, Double total) {
        return part == null || total == null || total == 0 ? null : part / total;
    }

    private static Integer toInt(Double v) {
        return v == null ? null : (int) Math.round(v);
    }
}
