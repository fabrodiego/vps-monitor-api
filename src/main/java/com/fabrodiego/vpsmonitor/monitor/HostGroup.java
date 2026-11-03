package com.fabrodiego.vpsmonitor.monitor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fabrodiego.vpsmonitor.monitor.MonitorDtos.AppKind;

/**
 * Interpreta o groupname do process-exporter, no formato "{{.Cgroups}}|{{.Comm}}".
 * Exemplo: "[/../../../../system.slice/postgresql@16-main.service]|postgres".
 * O "/../" vem do cgroup namespace do container do exporter e e descartado.
 *
 * @param kind   null quando o processo e de um pod (ja medido pelo cAdvisor, nao conta de novo)
 * @param name   nome mostrado no site
 * @param detail de onde veio (servico, sessao), para ajudar a dar um nome
 */
public record HostGroup(AppKind kind, String name, String detail, String command) {

    private static final Pattern SESSION = Pattern.compile("user-(\\d+)\\.slice/session-(\\d+)\\.scope$");
    private static final Pattern USER_MANAGER = Pattern.compile("user-(\\d+)\\.slice/user@\\d+\\.service");

    public static HostGroup parse(String groupname) {
        int bar = groupname.lastIndexOf('|');
        String cgroups = bar < 0 ? groupname : groupname.substring(0, bar);
        String command = bar < 0 ? "" : groupname.substring(bar + 1);
        String path = firstPath(cgroups);

        if (path.isEmpty() || path.equals("/") || path.contains("kubepods")) {
            return new HostGroup(null, command, "pod", command);
        }
        String last = path.substring(path.lastIndexOf('/') + 1);
        if (last.endsWith(".service")) {
            String unit = last.substring(0, last.length() - ".service".length());
            return new HostGroup(AppKind.SERVICO, unit, "serviço " + last, command);
        }
        Matcher session = SESSION.matcher(path);
        if (session.find()) {
            return new HostGroup(AppKind.SEM_NOME, command,
                "sessão " + session.group(2) + " do usuário uid " + session.group(1), command);
        }
        Matcher userManager = USER_MANAGER.matcher(path);
        if (userManager.find()) {
            return new HostGroup(AppKind.SISTEMA, "systemd do usuário " + userManager.group(1), path, command);
        }
        if (last.equals("init.scope")) {
            return new HostGroup(AppKind.SISTEMA, "systemd", "init.scope", command);
        }
        return new HostGroup(AppKind.SISTEMA, last.isEmpty() ? command : last, path, command);
    }

    /** "[a b]" -> "a" sem os "/.." do inicio. Em cgroup v2 so existe um caminho. */
    private static String firstPath(String cgroups) {
        String s = cgroups.strip();
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1).strip();
        }
        int space = s.indexOf(' ');
        if (space > 0) {
            s = s.substring(0, space);
        }
        int colon = s.lastIndexOf("::");
        if (colon >= 0) {
            s = s.substring(colon + 2);
        }
        while (s.startsWith("/..")) {
            s = s.substring(3);
        }
        return s.isEmpty() && cgroups.contains("/") ? "/" : s;
    }
}
