package com.libreria.sistema.controller;

import com.libreria.sistema.service.ConexionMovilService;
import com.libreria.sistema.service.ConexionMovilAccessTracker;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
@RequiredArgsConstructor
public class ConexionMovilPublicController {

    private static final DateTimeFormatter FECHA_FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final ConexionMovilService conexionMovilService;
    private final ConexionMovilAccessTracker accessTracker;

    @GetMapping(value = "/conexion-movil/acceso", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> accesoMovil(HttpServletRequest request) {
        accessTracker.registrar(request, "/conexion-movil/acceso");

        String serverUrl = conexionMovilService.generarServerUrl();
        String pingUrl = conexionMovilService.generarPingTextoUrl();
        String fecha = LocalDateTime.now().format(FECHA_FORMATO);
        String html = """
                <!doctype html>
                <html lang="es">
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>Acceso movil</title>
                    <style>
                        body{margin:0;font-family:Arial,sans-serif;background:#f4f7fb;color:#1f2937}
                        main{min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px}
                        section{width:100%%;max-width:430px;background:white;border-radius:16px;padding:24px;box-shadow:0 16px 40px rgba(15,23,42,.16)}
                        .ok{display:inline-flex;align-items:center;gap:8px;background:#dcfce7;color:#166534;border-radius:999px;padding:8px 12px;font-weight:700}
                        h1{font-size:24px;margin:18px 0 10px}
                        p{line-height:1.5;margin:8px 0;color:#475569}
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
                            <span class="ok">Conexión detectada</span>
                            <h1>El celular llegó al sistema</h1>
                            <p>Esta pantalla es liviana y sirve para confirmar que la red local permite entrar desde el teléfono.</p>
                            <code>%s</code>
                            <p>Fecha de prueba: <strong>%s</strong></p>
                            <a class="primary" href="%s/login">Entrar al sistema</a>
                            <a class="secondary" href="%s">Prueba en texto</a>
                            <small>Si esta pantalla abre, el problema ya no es WiFi ni Docker. Si no abre, revise firewall o aislamiento de red.</small>
                        </section>
                    </main>
                </body>
                </html>
                """.formatted(
                escapeHtml(conexionMovilService.generarAccesoMovilUrl()),
                escapeHtml(fecha),
                escapeHtml(serverUrl),
                escapeHtml(pingUrl)
        );

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noCache())
                .body(html);
    }

    @GetMapping(value = "/conexion-movil/ping", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> pingHtml(HttpServletRequest request) {
        accessTracker.registrar(request, "/conexion-movil/ping");

        String serverUrl = conexionMovilService.generarServerUrl();
        String fecha = LocalDateTime.now().format(FECHA_FORMATO);
        String html = """
                <!doctype html>
                <html lang="es">
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>Prueba movil</title>
                    <style>
                        body{margin:0;font-family:Arial,sans-serif;background:#f4f7fb;color:#1f2937}
                        main{min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px}
                        section{width:100%%;max-width:420px;background:white;border-radius:16px;padding:24px;box-shadow:0 16px 40px rgba(15,23,42,.16)}
                        .ok{display:inline-flex;align-items:center;gap:8px;background:#dcfce7;color:#166534;border-radius:999px;padding:8px 12px;font-weight:700}
                        h1{font-size:24px;margin:18px 0 10px}
                        p{line-height:1.5;margin:8px 0}
                        code{display:block;background:#f1f5f9;border-radius:10px;padding:12px;margin:12px 0;word-break:break-all}
                        a{display:block;text-align:center;background:#2563eb;color:white;text-decoration:none;border-radius:10px;padding:12px 14px;margin-top:18px;font-weight:700}
                        small{display:block;color:#64748b;margin-top:14px}
                    </style>
                </head>
                <body>
                    <main>
                        <section>
                            <span class="ok">OK conectado</span>
                            <h1>El celular llega al sistema</h1>
                            <p>Esta prueba confirma que la red, Docker y el puerto responden desde el telefono.</p>
                            <code>%s</code>
                            <p>Fecha de prueba: <strong>%s</strong></p>
                            <a href="%s/login">Entrar al sistema</a>
                            <small>Si esta pagina abre pero el login no, el problema ya no es la red: revise certificado, cache o sesion.</small>
                        </section>
                    </main>
                </body>
                </html>
                """.formatted(escapeHtml(serverUrl), escapeHtml(fecha), escapeHtml(serverUrl));

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noCache())
                .body(html);
    }

    @GetMapping(value = "/conexion-movil/ping.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public ResponseEntity<String> pingTexto(HttpServletRequest request) {
        accessTracker.registrar(request, "/conexion-movil/ping.txt");

        String contenido = "OK - Sistema accesible desde celular\n"
                + "URL: " + conexionMovilService.generarServerUrl() + "\n"
                + "Fecha: " + LocalDateTime.now().format(FECHA_FORMATO) + "\n";

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .cacheControl(CacheControl.noCache())
                .body(contenido);
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
