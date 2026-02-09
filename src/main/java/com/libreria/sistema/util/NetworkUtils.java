package com.libreria.sistema.util;

import lombok.extern.slf4j.Slf4j;

import java.net.*;
import java.util.*;

/**
 * Utilidad para detectar la dirección IP real de la máquina en la red local (LAN).
 * Filtra automáticamente localhost y direcciones no válidas para conexiones externas.
 */
@Slf4j
public final class NetworkUtils {

    private NetworkUtils() {
        // Utility class
    }

    /**
     * Obtiene la dirección IP de la red local.
     * Prioriza direcciones 192.168.x.x y 10.x.x.x que son típicas de redes locales.
     *
     * @return La IP local o "localhost" si no se puede determinar
     */
    public static String getLocalIpAddress() {
        try {
            List<String> candidateIps = new ArrayList<>();

            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();

                // Saltar interfaces inactivas o loopback
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }

                // Saltar interfaces virtuales (Docker, VirtualBox, etc.)
                String displayName = networkInterface.getDisplayName().toLowerCase();
                String name = networkInterface.getName().toLowerCase();
                if (isVirtualInterface(displayName) || isVirtualInterface(name)) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();

                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();

                    // Solo IPv4
                    if (!(address instanceof Inet4Address)) {
                        continue;
                    }

                    String ip = address.getHostAddress();

                    // Saltar localhost
                    if (ip.startsWith("127.")) {
                        continue;
                    }

                    // Priorizar IPs de red local típicas
                    if (ip.startsWith("192.168.") || ip.startsWith("10.")) {
                        log.info("IP de red local detectada: {} (interfaz: {})", ip, networkInterface.getDisplayName());
                        return ip;
                    }

                    // Guardar como candidato otras IPs privadas (172.16-31.x.x)
                    if (isPrivateIp(ip)) {
                        candidateIps.add(ip);
                    }
                }
            }

            // Si no encontramos 192.168 o 10.x, usar el primer candidato
            if (!candidateIps.isEmpty()) {
                String ip = candidateIps.get(0);
                log.info("IP candidata detectada: {}", ip);
                return ip;
            }

            // Método alternativo: conectarse a DNS público para detectar IP de salida
            String fallbackIp = getIpViaSocket();
            if (fallbackIp != null) {
                log.info("IP detectada via socket: {}", fallbackIp);
                return fallbackIp;
            }

        } catch (SocketException e) {
            log.error("Error al detectar IP de red: {}", e.getMessage());
        }

        log.warn("No se pudo detectar IP de red local, usando localhost");
        return "localhost";
    }

    /**
     * Verifica si la interfaz de red es virtual (Docker, VirtualBox, VMware, etc.)
     */
    private static boolean isVirtualInterface(String name) {
        if (name == null) return false;
        return name.contains("docker") ||
               name.contains("vbox") ||
               name.contains("virtualbox") ||
               name.contains("vmware") ||
               name.contains("vmnet") ||
               name.contains("veth") ||
               name.contains("virbr") ||
               name.contains("hyper-v") ||
               name.contains("virtual");
    }

    /**
     * Verifica si una IP es privada (RFC 1918)
     */
    private static boolean isPrivateIp(String ip) {
        if (ip == null) return false;

        // 10.0.0.0 - 10.255.255.255
        if (ip.startsWith("10.")) return true;

        // 192.168.0.0 - 192.168.255.255
        if (ip.startsWith("192.168.")) return true;

        // 172.16.0.0 - 172.31.255.255
        if (ip.startsWith("172.")) {
            try {
                String[] parts = ip.split("\\.");
                int second = Integer.parseInt(parts[1]);
                return second >= 16 && second <= 31;
            } catch (Exception e) {
                return false;
            }
        }

        return false;
    }

    /**
     * Método alternativo para detectar IP usando conexión de socket.
     * Crea una conexión UDP (sin enviar datos) a un servidor público
     * para determinar la IP de la interfaz de salida.
     */
    private static String getIpViaSocket() {
        try (DatagramSocket socket = new DatagramSocket()) {
            // Conectar a Google DNS (no envía datos realmente, solo determina ruta)
            socket.connect(InetAddress.getByName("8.8.8.8"), 53);
            String ip = socket.getLocalAddress().getHostAddress();

            // Verificar que no sea loopback
            if (!ip.startsWith("127.") && !ip.equals("0.0.0.0")) {
                return ip;
            }
        } catch (Exception e) {
            log.debug("No se pudo detectar IP via socket: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Obtiene información detallada de todas las interfaces de red.
     * Útil para diagnóstico.
     */
    public static List<Map<String, String>> getAllNetworkInfo() {
        List<Map<String, String>> result = new ArrayList<>();

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                Enumeration<InetAddress> addresses = ni.getInetAddresses();

                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();

                    if (address instanceof Inet4Address) {
                        Map<String, String> info = new HashMap<>();
                        info.put("interface", ni.getDisplayName());
                        info.put("name", ni.getName());
                        info.put("ip", address.getHostAddress());
                        info.put("isUp", String.valueOf(ni.isUp()));
                        info.put("isLoopback", String.valueOf(ni.isLoopback()));
                        info.put("isVirtual", String.valueOf(ni.isVirtual()));
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
     * Genera la URL completa del servidor incluyendo protocolo y puerto.
     *
     * @param useHttps Si usar HTTPS o HTTP
     * @param port Puerto del servidor
     * @return URL completa (ej: https://192.168.1.100:8443)
     */
    public static String getServerUrl(boolean useHttps, int port) {
        String ip = getLocalIpAddress();
        String protocol = useHttps ? "https" : "http";
        return String.format("%s://%s:%d", protocol, ip, port);
    }
}
