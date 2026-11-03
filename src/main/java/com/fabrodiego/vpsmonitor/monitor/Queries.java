package com.fabrodiego.vpsmonitor.monitor;

/** Todas as consultas PromQL do site. Fixas: o navegador so escolhe entre elas. */
final class Queries {

    private Queries() {
    }

    // containers reais (sem a linha de soma do pod nem o "pause")
    static final String CONTAINER = "container!=\"\",container!=\"POD\",image!=\"\"";
    static final String NET_DEVICES = "device!~\"lo|veth.*|cni.*|flannel.*|docker.*|br-.*\"";

    static final String CPU_USAGE = "1 - avg(rate(node_cpu_seconds_total{mode=\"idle\"}[2m]))";
    static final String CPU_CORES = "count(node_cpu_seconds_total{mode=\"idle\"})";
    static final String LOAD1 = "node_load1";
    static final String LOAD5 = "node_load5";
    static final String LOAD15 = "node_load15";
    static final String CPU_TEMP =
        "max(node_hwmon_temp_celsius * on(chip) group_left node_hwmon_chip_names{chip_name=\"coretemp\"})";

    static final String MEM_TOTAL = "node_memory_MemTotal_bytes";
    static final String MEM_AVAILABLE = "node_memory_MemAvailable_bytes";
    static final String SWAP_TOTAL = "node_memory_SwapTotal_bytes";
    static final String SWAP_FREE = "node_memory_SwapFree_bytes";

    static final String DISK_SIZE = "node_filesystem_size_bytes{mountpoint=\"/\"}";
    static final String DISK_AVAIL = "node_filesystem_avail_bytes{mountpoint=\"/\"}";

    static final String GPU_BUSY = "max(node_drm_gpu_busy_percent) / 100";
    static final String VRAM_USED = "max(node_drm_memory_vram_used_bytes)";
    static final String VRAM_TOTAL = "max(node_drm_memory_vram_size_bytes)";
    static final String GPU_TEMP =
        "max(node_hwmon_temp_celsius * on(chip) group_left node_hwmon_chip_names{chip_name=\"amdgpu\"})";

    static final String NET_RX = "sum(rate(node_network_receive_bytes_total{" + NET_DEVICES + "}[2m]))";
    static final String NET_TX = "sum(rate(node_network_transmit_bytes_total{" + NET_DEVICES + "}[2m]))";

    static final String UPTIME = "time() - node_boot_time_seconds";

    static final String PODS_RUNNING = "sum(kube_pod_status_phase{phase=\"Running\"})";
    static final String PODS_TOTAL = "count(kube_pod_info)";
    static final String SITES_UP = "sum(probe_success)";
    static final String SITES_TOTAL = "count(probe_success)";

    // ---------- apps ----------
    static final String NS_CPU = "sum by (namespace) (rate(container_cpu_usage_seconds_total{" + CONTAINER + "}[2m]))";
    static final String NS_MEM = "sum by (namespace) (container_memory_working_set_bytes{" + CONTAINER + "})";
    static final String NS_PODS = "count by (namespace) (kube_pod_info)";
    static final String PROC_CPU = "sum by (groupname) (rate(namedprocess_namegroup_cpu_seconds_total[2m]))";
    static final String PROC_MEM = "sum by (groupname) (namedprocess_namegroup_memory_bytes{memtype=\"resident\"})";
    static final String PROC_COUNT = "sum by (groupname) (namedprocess_namegroup_num_procs)";

    // ---------- pods ----------
    static final String POD_INFO = "kube_pod_info";
    static final String POD_PHASE = "kube_pod_status_phase == 1";
    static final String POD_READY = "kube_pod_status_ready{condition=\"true\"} == 1";
    static final String POD_WAITING = "kube_pod_container_status_waiting_reason == 1";
    static final String POD_RESTARTS = "sum by (namespace, pod) (kube_pod_container_status_restarts_total)";
    static final String POD_CPU = "sum by (namespace, pod) (rate(container_cpu_usage_seconds_total{" + CONTAINER + "}[2m]))";
    static final String POD_MEM = "sum by (namespace, pod) (container_memory_working_set_bytes{" + CONTAINER + "})";
    static final String POD_MEM_LIMIT =
        "sum by (namespace, pod) (kube_pod_container_resource_limits{resource=\"memory\"})";
    static final String POD_START = "kube_pod_start_time";

    // ---------- sites ----------
    static final String PROBE_SUCCESS = "probe_success";
    static final String PROBE_STATUS = "probe_http_status_code";
    static final String PROBE_DURATION = "probe_duration_seconds";
    static final String PROBE_CERT_DAYS = "(probe_ssl_earliest_cert_expiry - time()) / 86400";
    static final String PROBE_UPTIME_24H = "avg_over_time(probe_success[24h])";

    // ---------- banco ----------
    static final String PG_UP = "pg_up";
    static final String PG_MAX_CONN = "pg_settings_max_connections";
    static final String PG_SIZE = "pg_database_size_bytes{datname!=\"template0\"}";
    static final String PG_CONN = "sum by (datname) (pg_stat_activity_count)";
}
