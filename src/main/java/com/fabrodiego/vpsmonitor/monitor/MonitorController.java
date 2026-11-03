package com.fabrodiego.vpsmonitor.monitor;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.Alert;
import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.AppsResponse;
import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.DatabaseResponse;
import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.Overview;
import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.PodsResponse;
import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.SeriesResponse;
import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.Site;

/** Tudo so leitura. */
@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    private final MonitorService service;

    public MonitorController(MonitorService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public Overview overview() {
        return service.overview();
    }

    /** metric: cpu, ram, disk, gpu, temp, net. range: 1h, 6h, 24h, 7d. */
    @GetMapping("/series/{metric}")
    public SeriesResponse series(@PathVariable String metric, @RequestParam(defaultValue = "1h") String range) {
        SeriesMetric m = SeriesMetric.valueOf(metric.toUpperCase(Locale.ROOT));
        return service.series(m, SeriesMetric.Range.parse(range));
    }

    @GetMapping("/apps")
    public AppsResponse apps() {
        return service.apps();
    }

    @GetMapping("/pods")
    public PodsResponse pods() {
        return service.pods();
    }

    @GetMapping("/sites")
    public List<Site> sites() {
        return service.sites();
    }

    @GetMapping("/database")
    public DatabaseResponse database() {
        return service.database();
    }

    @GetMapping("/alerts")
    public List<Alert> alerts() {
        return service.alerts();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Parâmetro inválido");
    }
}
