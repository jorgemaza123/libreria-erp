package com.libreria.sistema.util;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utilidad para detectar la dirección IP real de la máquina en la red local (LAN).
 *
 * Se usa UNA SOLA VEZ en el primer arranque (o cuando el admin presiona "Recalcular IP").
 * La IP detectada se persiste en BD y no se vuelve a calcular en runtime.
 *
 * Algoritmo de detección (ejecutado bajo demanda):
 *   1. Variable de entorno HOST_IP (override para Docker / casos especiales)
 *   2. Si corre en Docker → resolver host.docker.internal
 *   3. Gateway del SO (route print / ip route)
 *   4. Enumeración de interfaces de red
 *   5. Fallback a localhost
 */
@Slf4j
public final class NetworkUtils {

    private static final Set<String> VIRTUAL_KEYWORDS = Set.of(
            "docker", "vbox", "virtualbox", "vmware", "vmnet", "veth",
            "virbr", "hyper-v", "virtual", "tap0", "tun0", "tun", "tap",
            "wsl", "podman", "br-", "vethernet", "loopback",
            "nat", "vpn", "zt", "zerotier", "tailscale", "hamachi",
            "isatap", "teredo", "6to4"
    );

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "\\b(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\b"
    );

    private NetworkUtils() {
    }

    // =======================================================================
    //  MÉTODO PRINCIPAL — llamado una sola vez al instalar / recalcular
    // =======================================================================

    /**
     * Detecta la IP LAN real del host. Se ejecuta bajo demanda, no en runtime.
     */
    public static String detectarIpLan() {
        try {
            // 1. Override explícito via variable de entorno
            String envIp = System.getenv("HOST_IP");
            if (envIp != null && !envIp.isBlank() && isPrivateIp(envIp.trim())) {
                log.info("IP obtenida de variable de entorno HOST_IP: {}", envIp.trim());
                return envIp.trim();
            }

            // 2. Si estamos dentro de Docker, resolver la IP del host
            if (isRunningInDocker()) {
                String hostIp = resolverHostDockerInternal();
                if (hostIp != null && isLanIp(hostIp)) {
                    log.info("IP del host detectada via Docker: {}", hostIp);
                    return hostIp;
                }
                log.warn("Dentro de Docker pero no se pudo resolver IP del host — continuando con otros métodos");
            }

            // 3. Intentar detectar via gateway del SO
            String gatewayIp = detectarIpViaGateway();
            if (gatewayIp != null) {
                log.info("IP detectada via gateway del SO: {}", gatewayIp);
                return gatewayIp;
            }

            // 4. Fallback — enumerar interfaces activas
            String interfaceIp = detectarIpViaInterfaces();
            if (interfaceIp != null) {
                log.info("IP detectada via interfaces de red: {}", interfaceIp);
                return interfaceIp;
            }

        } catch (Exception e) {
            log.error("Error al detectar IP de red: {}", e.getMessage());
        }

        log.warn("No se pudo detectar IP de red local, usando localhost");
        return "localhost";
    }

    // =======================================================================
    //  DETECCIÓN DOCKER
    // =======================================================================

    static boolean isRunningInDocker() {
        try {
            if (new java.io.File("/.dockerenv").exists()) return true;
        } catch (Exception ignored) {}

        try {
            java.nio.file.Path cgroupPath = java.nio.file.Path.of("/proc/1/cgroup");
            if (java.nio.file.Files.exists(cgroupPath)) {
                String content = java.nio.file.Files.readString(cgroupPath).toLowerCase();
                if (content.contains("docker") || content.contains("containerd") || content.contains("/lxc/")) {
                    return true;
                }
            }
        } catch (Exception ignored) {}

        String dsUrl = System.getenv("SPRING_DATASOURCE_URL");
        if (dsUrl != null && dsUrl.contains("://db:")) return true;

        return false;
    }

    private static String resolverHostDockerInternal() {
        try {
            InetAddress addr = InetAddress.getByName("host.docker.internal");
            String ip = addr.getHostAddress();
            if (ip != null && !ip.equals("127.0.0.1") && isPrivateIp(ip)) {
                return ip;
            }
        } catch (Exception e) {
            log.debug("No se pudo resolver host.docker.internal: {}", e.getMessage());
        }

        // Fallback: default gateway del contenedor (es la IP del host en bridge)
        try {
            ProcessBuilder pb = new ProcessBuilder("ip", "route", "show", "default");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining(" "));
            }
            if (process.waitFor(5, TimeUnit.SECONDS)) {
                Matcher matcher = Pattern.compile("default\\s+via\\s+(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})").matcher(output);
                if (matcher.find()) {
                    String gw = matcher.group(1);
                    if (isPrivateIp(gw)) return gw;
                }
            } else {
                process.destroyForcibly();
            }
        } catch (Exception e) {
            log.debug("Fallback gateway Docker falló: {}", e.getMessage());
        }
        return null;
    }

    // =======================================================================
    //  DETECCIÓN VIA GATEWAY DEL SO
    // =======================================================================

    private static String detectarIpViaGateway() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("windows");
        try {
            return isWindows ? detectarIpViaGatewayWindows() : detectarIpViaGatewayLinux();
        } catch (Exception e) {
            log.debug("No se pudo detectar gateway: {}", e.getMessage());
            return null;
        }
    }

    private static String detectarIpViaGatewayWindows() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "route", "print", "0.0.0.0");
        pb.redirectErrorStream(true);
        Process process = pb.start();

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line.trim());
            }
        }

        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return null;
        }

        List<IpCandidate> candidates = new ArrayList<>();
        for (String line : lines) {
            if (!line.startsWith("0.0.0.0")) continue;

            Matcher matcher = IPV4_PATTERN.matcher(line);
            List<String> ipsInLine = new ArrayList<>();
            while (matcher.find()) ipsInLine.add(matcher.group(1));

            int metric = extractLastNumber(line);
            for (String ip : ipsInLine) {
                if ("0.0.0.0".equals(ip)) continue;
                if (isPrivateIp(ip)) {
                    candidates.add(new IpCandidate(ip, metric, getIpPriority(ip)));
                }
            }
        }

        if (candidates.isEmpty()) return null;

        // Ordenar: primero LAN real (192.168 > 10 > 172), luego por métrica
        candidates.sort(Comparator
                .comparingInt(IpCandidate::rangePriority)
                .thenComparingInt(IpCandidate::metric));

        // Retornar el primer candidato que sea interfaz real
        for (IpCandidate c : candidates) {
            if (validarIpEnInterfazReal(c.ip())) {
                return c.ip();
            }
        }
        return candidates.get(0).ip();
    }

    private static String detectarIpViaGatewayLinux() throws Exception {
        // ip route get 1.1.1.1 → buscar "src X.X.X.X"
        String ip = ejecutarYBuscarSrc("ip", "route", "get", "1.1.1.1");
        if (ip != null && isLanIp(ip)) return ip;

        ip = ejecutarYBuscarSrc("ip", "route", "show", "default");
        if (ip != null && isLanIp(ip)) return ip;

        // Si la IP es 172.x (Docker), buscar mejor alternativa
        if (ip != null && isDockerLikeIp(ip)) {
            String lanIp = detectarIpViaInterfaces();
            if (lanIp != null) return lanIp;
        }

        return ip;
    }

    private static String ejecutarYBuscarSrc(String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining(" "));
            }
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) return null;

            Matcher matcher = Pattern.compile("src\\s+(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})").matcher(output);
            if (matcher.find()) {
                String ip = matcher.group(1);
                if (isPrivateIp(ip)) return ip;
            }
        } catch (Exception e) {
            log.debug("Comando {} falló: {}", String.join(" ", command), e.getMessage());
        }
        return null;
    }

    // =======================================================================
    //  FALLBACK: ENUMERACIÓN DE INTERFACES
    // =======================================================================

    private static String detectarIpViaInterfaces() {
        List<String> candidates = listarIpsCandidatas();
        for (String ip : candidates) {
            if (isLanIp(ip)) return ip;
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static List<String> listarIpsCandidatas() {
        List<String> candidates = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return candidates;

            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                if (isVirtualInterface(ni)) continue;

                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!(addr instanceof Inet4Address)) continue;
                    String ip = addr.getHostAddress();
                    if (ip.startsWith("127.")) continue;
                    if (isPrivateIp(ip) && !candidates.contains(ip)) {
                        candidates.add(ip);
                    }
                }
            }
        } catch (SocketException e) {
            log.error("Error al enumerar interfaces: {}", e.getMessage());
        }
        candidates.sort(Comparator.comparingInt(NetworkUtils::getIpPriority));
        return candidates;
    }

    // =======================================================================
    //  VALIDACIÓN
    // =======================================================================

    private static boolean validarIpEnInterfazReal(String ip) {
        if (ip == null || !isPrivateIp(ip)) return false;
        try {
            InetAddress targetAddr = InetAddress.getByName(ip);
            NetworkInterface ni = NetworkInterface.getByInetAddress(targetAddr);
            if (ni == null || !ni.isUp() || ni.isLoopback()) return false;
            return !isVirtualInterface(ni);
        } catch (Exception e) {
            return false;
        }
    }

    // =======================================================================
    //  UTILIDADES
    // =======================================================================

    private static boolean isVirtualInterface(NetworkInterface ni) {
        if (ni.isVirtual()) return true;
        String displayName = ni.getDisplayName().toLowerCase();
        String name = ni.getName().toLowerCase();
        for (String keyword : VIRTUAL_KEYWORDS) {
            if (displayName.contains(keyword) || name.contains(keyword)) return true;
        }
        if (displayName.contains("switch") || displayName.contains("dockernat")) return true;
        return false;
    }

    static boolean isPrivateIp(String ip) {
        if (ip == null) return false;
        if (ip.startsWith("10.")) return true;
        if (ip.startsWith("192.168.")) return true;
        if (ip.startsWith("172.")) {
            try {
                int second = Integer.parseInt(ip.split("\\.")[1]);
                return second >= 16 && second <= 31;
            } catch (Exception e) { return false; }
        }
        return false;
    }

    static boolean isLanIp(String ip) {
        if (ip == null) return false;
        return ip.startsWith("192.168.") || ip.startsWith("10.");
    }

    static boolean isDockerLikeIp(String ip) {
        if (ip == null) return false;
        if (ip.startsWith("172.")) {
            try {
                int second = Integer.parseInt(ip.split("\\.")[1]);
                return second >= 16 && second <= 31;
            } catch (Exception e) { return false; }
        }
        return false;
    }

    private static int getIpPriority(String ip) {
        if (ip.startsWith("192.168.")) return 1;
        if (ip.startsWith("10.")) return 2;
        if (ip.startsWith("172.")) return 3;
        return 4;
    }

    private static int extractLastNumber(String line) {
        Matcher matcher = Pattern.compile("(\\d+)\\s*$").matcher(line.trim());
        if (matcher.find()) {
            try { return Integer.parseInt(matcher.group(1)); }
            catch (NumberFormatException e) { return Integer.MAX_VALUE; }
        }
        return Integer.MAX_VALUE;
    }

    private record IpCandidate(String ip, int metric, int rangePriority) {}
}
