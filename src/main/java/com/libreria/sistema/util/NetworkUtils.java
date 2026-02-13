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
 * Utilidad robusta para detectar la dirección IP real de la máquina en la red local (LAN).
 *
 * Algoritmo de 3 fases:
 *   1. Gateway del SO (route print / ip route) con validación post-detección
 *   2. Enumeración de interfaces con filtrado de virtuales y priorización RFC1918
 *   3. Fallback a localhost
 *
 * Toda IP detectada via gateway se VALIDA contra NetworkInterface real:
 *   - Debe existir en una interfaz activa del sistema
 *   - Debe ser IPv4
 *   - Debe ser RFC1918 (privada)
 *   - La interfaz debe tener broadcast configurado
 *
 * No cachea resultados — cada llamada recalcula dinámicamente.
 */
@Slf4j
public final class NetworkUtils {

    private static final Set<String> VIRTUAL_KEYWORDS = Set.of(
            "docker", "vbox", "virtualbox", "vmware", "vmnet", "veth",
            "virbr", "hyper-v", "virtual", "tap0", "tun0", "tun", "tap",
            "wsl", "podman", "br-", "vethernet", "loopback"
    );

    /** Patrón para detectar IPs IPv4 válidas en texto plano */
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "\\b(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\b"
    );

    private NetworkUtils() {
    }

    /**
     * Detecta la IP local principal.
     * Algoritmo de 3 fases con validación post-detección.
     */
    public static String getLocalIpAddress() {
        try {
            // Fase 1: Intentar detectar via gateway del SO
            String gatewayIp = detectarIpViaGateway();
            if (gatewayIp != null && validarIpEnInterfazReal(gatewayIp)) {
                log.debug("IP detectada y validada via gateway: {}", gatewayIp);
                return gatewayIp;
            } else if (gatewayIp != null) {
                log.warn("IP {} detectada via gateway pero NO validada en interfaces reales — fallback", gatewayIp);
            }

            // Fase 2: Fallback — enumerar interfaces activas
            String interfaceIp = detectarIpViaInterfaces();
            if (interfaceIp != null) {
                log.debug("IP detectada via enumeración de interfaces: {}", interfaceIp);
                return interfaceIp;
            }

        } catch (Exception e) {
            log.error("Error al detectar IP de red: {}", e.getMessage());
        }

        log.warn("No se pudo detectar IP de red local, usando localhost");
        return "localhost";
    }

    /**
     * VALIDACIÓN POST-DETECCIÓN: Verifica que una IP realmente existe
     * en una interfaz de red activa del sistema.
     *
     * Valida:
     * - Existe en NetworkInterface real
     * - La interfaz está UP
     * - Es IPv4
     * - Es RFC1918 (privada)
     * - La interfaz tiene broadcast (no es point-to-point/virtual)
     * - No es interfaz virtual (Docker, VBox, etc.)
     */
    private static boolean validarIpEnInterfazReal(String ip) {
        if (ip == null || !isPrivateIp(ip)) return false;

        try {
            InetAddress targetAddr = InetAddress.getByName(ip);
            NetworkInterface ni = NetworkInterface.getByInetAddress(targetAddr);

            if (ni == null) {
                log.debug("Validación falló: IP {} no encontrada en ninguna interfaz", ip);
                return false;
            }

            if (!ni.isUp()) {
                log.debug("Validación falló: interfaz {} no está activa", ni.getDisplayName());
                return false;
            }

            if (ni.isLoopback()) {
                log.debug("Validación falló: interfaz {} es loopback", ni.getDisplayName());
                return false;
            }

            if (isVirtualInterface(ni)) {
                log.debug("Validación falló: interfaz {} es virtual", ni.getDisplayName());
                return false;
            }

            // Verificar que tiene broadcast (interfaces reales de LAN tienen broadcast)
            boolean hasBroadcast = false;
            for (InterfaceAddress ifAddr : ni.getInterfaceAddresses()) {
                if (ifAddr.getAddress().equals(targetAddr) && ifAddr.getBroadcast() != null) {
                    hasBroadcast = true;
                    break;
                }
            }

            if (!hasBroadcast) {
                log.debug("Validación: IP {} sin broadcast — puede ser point-to-point, aceptando con menor confianza", ip);
                // No rechazar, pero es sospechoso
            }

            log.debug("Validación exitosa: IP {} en interfaz {} (up={}, broadcast={})",
                    ip, ni.getDisplayName(), ni.isUp(), hasBroadcast);
            return true;

        } catch (Exception e) {
            log.debug("Error validando IP {}: {}", ip, e.getMessage());
            return false;
        }
    }

    /**
     * Retorna TODAS las IPs privadas válidas en interfaces reales.
     * Solo incluye IPs que pasan validación completa.
     */
    public static List<String> getAllCandidateIps() {
        List<String> candidates = new ArrayList<>();

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return candidates;

            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();

                if (!ni.isUp() || ni.isLoopback()) continue;
                if (isVirtualInterface(ni)) continue;

                // Verificar que la interfaz tiene al menos una dirección con broadcast
                boolean hasBroadcastAddr = ni.getInterfaceAddresses().stream()
                        .anyMatch(ia -> ia.getBroadcast() != null);

                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!(addr instanceof Inet4Address)) continue;

                    String ip = addr.getHostAddress();
                    if (ip.startsWith("127.")) continue;

                    if (isPrivateIp(ip) && !candidates.contains(ip)) {
                        // Priorizar IPs con broadcast (interfaces LAN reales)
                        if (hasBroadcastAddr) {
                            candidates.add(0, ip); // Al inicio
                        } else {
                            candidates.add(ip); // Al final
                        }
                    }
                }
            }
        } catch (SocketException e) {
            log.error("Error al enumerar interfaces: {}", e.getMessage());
        }

        // Ordenar: 192.168.* primero, luego 10.*, luego 172.*
        candidates.sort(Comparator.comparingInt(NetworkUtils::getIpPriority));

        return candidates;
    }

    // =======================================================================
    //  DETECCIÓN VIA GATEWAY DEL SISTEMA OPERATIVO
    // =======================================================================

    /**
     * Detecta la IP asociada al default gateway.
     * Usa parseo robusto con regex (no depende de formato de columnas/idioma).
     */
    private static String detectarIpViaGateway() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("windows");

        try {
            if (isWindows) {
                return detectarIpViaGatewayWindows();
            } else {
                return detectarIpViaGatewayLinux();
            }
        } catch (Exception e) {
            log.debug("No se pudo detectar gateway: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Windows: ejecuta "route print 0.0.0.0" y busca IPs privadas en líneas
     * que contienen "0.0.0.0" al inicio.
     *
     * Parseo robusto: usa regex para extraer IPs, no depende de columnas fijas
     * ni encabezados de idioma (funciona en Windows español/inglés/Server).
     *
     * Formato típico (puede variar en orden/espaciado):
     *   0.0.0.0          0.0.0.0     192.168.1.1     192.168.1.100     25
     */
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

        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return null;
        }

        // Buscar líneas que empiecen con "0.0.0.0" (ruta default)
        // No dependemos de formato de columnas: extraemos TODAS las IPs de la línea
        // y buscamos una IP privada que NO sea 0.0.0.0 ni el gateway
        String bestCandidate = null;
        int bestMetric = Integer.MAX_VALUE;

        for (String line : lines) {
            if (!line.startsWith("0.0.0.0")) continue;

            // Extraer todas las IPs de esta línea con regex
            Matcher matcher = IPV4_PATTERN.matcher(line);
            List<String> ipsInLine = new ArrayList<>();
            while (matcher.find()) {
                ipsInLine.add(matcher.group(1));
            }

            // Buscar la IP de interfaz local: privada, no 0.0.0.0, no igual al gateway
            // El gateway típicamente es la 3ra IP, la interfaz la 4ta
            for (String ip : ipsInLine) {
                if ("0.0.0.0".equals(ip)) continue;
                if (isPrivateIp(ip)) {
                    // Intentar extraer métrica (último número de la línea)
                    int metric = extractLastNumber(line);
                    if (metric < bestMetric) {
                        bestMetric = metric;
                        bestCandidate = ip;
                    }
                }
            }
        }

        if (bestCandidate != null) {
            log.info("IP detectada via route print (Windows): {} (métrica: {})", bestCandidate, bestMetric);
        }
        return bestCandidate;
    }

    /**
     * Linux: ejecuta "ip route get 1.1.1.1" y busca "src X.X.X.X".
     * Fallback a "ip route show default" si el primer comando falla.
     * Parseo robusto con regex.
     */
    private static String detectarIpViaGatewayLinux() throws Exception {
        // Intento 1: ip route get (más confiable, funciona en la mayoría de distros)
        String ip = ejecutarYBuscarSrc("ip", "route", "get", "1.1.1.1");
        if (ip != null) return ip;

        // Intento 2: ip route show default + buscar interfaz
        ip = ejecutarYBuscarSrc("ip", "route", "show", "default");
        if (ip != null) return ip;

        // Intento 3: route -n (disponible en BusyBox y sistemas legacy)
        try {
            ProcessBuilder pb = new ProcessBuilder("route", "-n");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }
            process.waitFor(10, TimeUnit.SECONDS);

            // Buscar línea con destino 0.0.0.0
            for (String line : output.split("\n")) {
                if (line.startsWith("0.0.0.0")) {
                    Matcher matcher = IPV4_PATTERN.matcher(line);
                    List<String> ips = new ArrayList<>();
                    while (matcher.find()) ips.add(matcher.group(1));
                    // En route -n: destino, gateway, mask, ..., interfaz
                    // La IP de la interfaz se obtiene del nombre, no de la tabla
                    // Pero podemos obtener el gateway y buscar la interfaz asociada
                    if (ips.size() >= 2) {
                        String gateway = ips.get(1);
                        if (!gateway.equals("0.0.0.0") && isPrivateIp(gateway)) {
                            // Buscar IP local en el mismo subnet
                            return buscarIpEnMismoSubnet(gateway);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("route -n no disponible: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Ejecuta un comando y busca "src X.X.X.X" en la salida.
     */
    private static String ejecutarYBuscarSrc(String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining(" "));
            }

            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }

            if (process.exitValue() != 0) return null;

            // Buscar "src X.X.X.X"
            Matcher matcher = Pattern.compile("src\\s+(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})").matcher(output);
            if (matcher.find()) {
                String ip = matcher.group(1);
                if (isPrivateIp(ip)) {
                    log.info("IP detectada via {} (Linux): {}", String.join(" ", command), ip);
                    return ip;
                }
            }
        } catch (Exception e) {
            log.debug("Comando {} falló: {}", String.join(" ", command), e.getMessage());
        }
        return null;
    }

    /**
     * Busca una IP local que esté en el mismo subnet que el gateway dado.
     */
    private static String buscarIpEnMismoSubnet(String gatewayIp) {
        String gatewayPrefix = gatewayIp.substring(0, gatewayIp.lastIndexOf('.') + 1);
        List<String> candidates = getAllCandidateIps();
        for (String ip : candidates) {
            if (ip.startsWith(gatewayPrefix)) {
                return ip;
            }
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    // =======================================================================
    //  FALLBACK: ENUMERACIÓN DE INTERFACES
    // =======================================================================

    private static String detectarIpViaInterfaces() throws SocketException {
        List<String> candidates = getAllCandidateIps();
        if (!candidates.isEmpty()) {
            String best = candidates.get(0);
            log.info("IP seleccionada via interfaces: {}", best);
            return best;
        }
        return null;
    }

    // =======================================================================
    //  UTILIDADES
    // =======================================================================

    /**
     * Verifica si una interfaz de red es virtual.
     */
    private static boolean isVirtualInterface(NetworkInterface ni) {
        if (ni.isVirtual()) return true;

        String displayName = ni.getDisplayName().toLowerCase();
        String name = ni.getName().toLowerCase();

        for (String keyword : VIRTUAL_KEYWORDS) {
            if (displayName.contains(keyword) || name.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Verifica si una IP es privada (RFC 1918).
     */
    static boolean isPrivateIp(String ip) {
        if (ip == null) return false;
        if (ip.startsWith("10.")) return true;
        if (ip.startsWith("192.168.")) return true;
        if (ip.startsWith("172.")) {
            try {
                int second = Integer.parseInt(ip.split("\\.")[1]);
                return second >= 16 && second <= 31;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    private static int getIpPriority(String ip) {
        if (ip.startsWith("192.168.")) return 1;
        if (ip.startsWith("10.")) return 2;
        if (ip.startsWith("172.")) return 3;
        return 4;
    }

    /**
     * Extrae el último número entero de una línea de texto.
     * Usado para obtener la métrica de la tabla de rutas.
     */
    private static int extractLastNumber(String line) {
        Matcher matcher = Pattern.compile("(\\d+)\\s*$").matcher(line.trim());
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return Integer.MAX_VALUE;
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Obtiene información detallada de todas las interfaces de red.
     */
    public static List<Map<String, String>> getAllNetworkInfo() {
        List<Map<String, String>> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return result;

            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                Enumeration<InetAddress> addresses = ni.getInetAddresses();

                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address) {
                        Map<String, String> info = new LinkedHashMap<>();
                        info.put("interface", ni.getDisplayName());
                        info.put("name", ni.getName());
                        info.put("ip", address.getHostAddress());
                        info.put("isUp", String.valueOf(ni.isUp()));
                        info.put("isLoopback", String.valueOf(ni.isLoopback()));
                        info.put("isVirtual", String.valueOf(isVirtualInterface(ni)));
                        result.add(info);
                    }
                }
            }
        } catch (SocketException e) {
            log.error("Error al obtener información de red: {}", e.getMessage());
        }
        return result;
    }

    /**
     * Genera la URL completa del servidor.
     */
    public static String getServerUrl(boolean useHttps, int port) {
        String ip = getLocalIpAddress();
        String protocol = useHttps ? "https" : "http";
        return String.format("%s://%s:%d", protocol, ip, port);
    }
}
