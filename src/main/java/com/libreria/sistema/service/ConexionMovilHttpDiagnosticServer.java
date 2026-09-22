package com.libreria.sistema.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConexionMovilHttpDiagnosticServer {

    private static final DateTimeFormatter FECHA_FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final ConexionMovilService conexionMovilService;
    private final ConexionMovilAccessTracker accessTracker;

    @Value("${conexion-movil.http-diagnostico.enabled:true}")
    private boolean enabled;

    @Value("${conexion-movil.http-diagnostico.port:8081}")
    private int port;

    @Value("${server.ssl.enabled:true}")
    private boolean sslEnabled;

    private HttpServer server;
    private ExecutorService executor;

    @PostConstruct
    public void start() {
        if (!enabled || !sslEnabled || port <= 0) {
            log.info("Diagnóstico HTTP móvil desactivado.");
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
            server.createContext("/conexion-movil/http-ping", this::handlePing);
            server.createContext("/conexion-movil/http-acceso", this::handleAcceso);
            executor = Executors.newCachedThreadPool(runnable -> {
                Thread thread = new Thread(runnable, "conexion-movil-http-diagnostic");
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(executor);
            server.start();
            log.info("Diagnóstico HTTP móvil activo en http://0.0.0.0:{}/conexion-movil/http-ping", port);
        } catch (IOException e) {
            log.warn("No se pudo iniciar diagnóstico HTTP móvil en puerto {}: {}", port, e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void handlePing(HttpExchange exchange) throws IOException {
        registrar(exchange, "/conexion-movil/http-ping");
        String contenido = "OK - El celular llega al servidor HTTP de diagnostico\n"
                + "HTTPS principal: " + conexionMovilService.generarAccesoMovilUrl() + "\n"
                + "Fecha: " + LocalDateTime.now().format(FECHA_FORMATO) + "\n";
        responder(exchange, 200, "text/plain; charset=utf-8", contenido);
    }

    private void handleAcceso(HttpExchange exchange) throws IOException {
        registrar(exchange, "/conexion-movil/http-acceso");
        String httpsUrl = conexionMovilService.generarAccesoMovilUrl();
        String httpPingUrl = conexionMovilService.generarHttpDiagnosticoPingUrl();
        String fecha = LocalDateTime.now().format(FECHA_FORMATO);
        String html = """
                <!doctype html>
                <html lang="es">
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>Diagnostico movil</title>
                    <style>
                        body{margin:0;font-family:Arial,sans-serif;background:#f4f7fb;color:#172033}
                        main{min-height:100vh;display:flex;align-items:center;justify-content:center;padding:22px}
                        section{width:100%%;max-width:430px;background:white;border-radius:16px;padding:24px;box-shadow:0 16px 40px rgba(15,23,42,.16)}
                        .ok{display:inline-flex;align-items:center;gap:8px;background:#dcfce7;color:#166534;border-radius:999px;padding:8px 12px;font-weight:700}
                        h1{font-size:23px;margin:18px 0 10px}
                        p{line-height:1.5;margin:9px 0;color:#475569}
                        code{display:block;background:#f1f5f9;border-radius:10px;padding:12px;margin:12px 0;word-break:break-all;color:#0f172a}
                        a{display:block;text-align:center;text-decoration:none;border-radius:10px;padding:13px 14px;margin-top:12px;font-weight:700}
                        .primary{background:#2563eb;color:white}
                        .secondary{background:#e0f2fe;color:#075985}
                        small{display:block;color:#64748b;margin-top:14px}
                    </style>
                </head>
                <body>
                    <main>
                        <section>
                            <span class="ok">Red detectada por HTTP</span>
                            <h1>El celular si llega a esta PC</h1>
                            <p>Esta prueba usa el puerto HTTP auxiliar. Sirve para separar problemas de red de problemas del certificado HTTPS.</p>
                            <code>%s</code>
                            <p>Fecha de prueba: <strong>%s</strong></p>
                            <a class="primary" href="%s">Probar HTTPS principal</a>
                            <a class="secondary" href="%s">Prueba HTTP en texto</a>
                            <small>Si HTTP abre pero HTTPS no, reinicie Docker para renovar el certificado y permita tambien el puerto 8443 en firewall.</small>
                        </section>
                    </main>
                </body>
                </html>
                """.formatted(
                escapeHtml(httpsUrl),
                escapeHtml(fecha),
                escapeHtml(httpsUrl),
                escapeHtml(httpPingUrl)
        );
        responder(exchange, 200, "text/html; charset=utf-8", html);
    }

    private void registrar(HttpExchange exchange, String ruta) {
        String ip = exchange.getRemoteAddress() != null && exchange.getRemoteAddress().getAddress() != null
                ? exchange.getRemoteAddress().getAddress().getHostAddress()
                : "";
        String userAgent = exchange.getRequestHeaders().getFirst("User-Agent");
        accessTracker.registrar(ip, userAgent, ruta);
    }

    private void responder(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store, no-cache, must-revalidate");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
