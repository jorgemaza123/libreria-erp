package com.libreria.sistema.service;

import com.libreria.sistema.model.Configuracion;
import com.libreria.sistema.repository.ConfiguracionRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class ConfiguracionService {

    private final ConfiguracionRepository repository;

    public ConfiguracionService(ConfiguracionRepository repository) {
        this.repository = repository;
    }

    public Configuracion obtenerConfiguracion() {
        return repository.findById(1L).orElseGet(this::crearConfiguracionPorDefecto);
    }

    private Configuracion crearConfiguracionPorDefecto() {
        Configuracion config = new Configuracion();

        // Datos de empresa
        config.setNombreEmpresa("MI EMPRESA S.A.C.");
        config.setRuc("20000000001");
        config.setDireccion("Dirección por configurar");
        config.setTelefono("(01) 000-0000");
        config.setEmail("contacto@miempresa.com");
        config.setSlogan("Tu empresa de confianza");
        config.setHorarioAtencion("Lun-Vie: 9am-7pm, Sáb: 9am-2pm");

        // Personalización visual - Colores por defecto
        config.setColorPrimario("#007bff");
        config.setColorSecundario("#6c757d");
        config.setColorExito("#28a745");
        config.setColorPeligro("#dc3545");
        config.setColorAdvertencia("#ffc107");
        config.setColorInfo("#17a2b8");
        config.setColorOscuro("#343a40");
        config.setColorClaro("#f8f9fa");
        config.setColorBronce("#cd7f32");

        // Configuración de reportes
        config.setMostrarLogoEnReportes(true);
        config.setPiePaginaReportes("Gracias por su preferencia");
        config.setEncabezadoReportes("Documento generado automáticamente");
        config.setFormatoFechaReportes("dd/MM/yyyy");
        config.setFormatoMoneda("S/");

        // Configuración de sistema
        config.setItemsPorPagina(10);
        config.setStockMinimo(5);
        config.setDiasVencimientoCredito(30);
        config.setDiasDevolucion(30);
        config.setIgvPorcentaje(new BigDecimal("18.00"));

        // Control financiero y de caja
        config.setAperturaCajaObligatoria(true);
        config.setCierreCajaCiego(true);
        config.setLimiteEfectivoCaja(new BigDecimal("2000.00"));

        // Reglas de negocio
        config.setPermitirStockNegativo(false);
        config.setPermitirVentaFraccionada(false);
        config.setPorcentajeDescuentoMaximo(new BigDecimal("10.00"));
        config.setPreciosIncluyenImpuesto(true);

        // Facturación electrónica
        config.setFacturacionEndpoint("");
        config.setFacturacionToken("");
        config.setCertificadoDigitalRuta("");
        config.setClaveCertificado("");
        config.setModoProduccion(false);

        return repository.save(config);
    }

    public void guardarConfiguracion(Configuracion config) {
        config.setId(1L); // Forzamos que siempre sea el ID 1 (Singleton en BD)
        repository.save(config);
    }

    // ========== MÉTODOS HELPER PARA IGV ==========

    /**
     * Obtiene el porcentaje de IGV (ej: 18.00)
     */
    public BigDecimal getIgvPorcentaje() {
        Configuracion config = obtenerConfiguracion();
        return config.getIgvPorcentaje() != null ? config.getIgvPorcentaje() : new BigDecimal("18.00");
    }

    /**
     * Obtiene el factor de IGV para cálculos (ej: 1.18)
     */
    public BigDecimal getIgvFactor() {
        BigDecimal porcentaje = getIgvPorcentaje();
        return BigDecimal.ONE.add(porcentaje.divide(new BigDecimal("100")));
    }

    /**
     * Obtiene el label para mostrar en reportes (ej: "IGV (18%)")
     */
    public String getIgvLabel() {
        BigDecimal porcentaje = getIgvPorcentaje();
        return "IGV (" + porcentaje.stripTrailingZeros().toPlainString() + "%)";
    }

    /**
     * Obtiene el porcentaje como string para SUNAT (ej: "18.00")
     */
    public String getIgvPorcentajeString() {
        return getIgvPorcentaje().setScale(2).toPlainString();
    }

    public void restaurarColoresPorDefecto() {
        Configuracion config = obtenerConfiguracion();
        config.setColorPrimario("#007bff");
        config.setColorSecundario("#6c757d");
        config.setColorExito("#28a745");
        config.setColorPeligro("#dc3545");
        config.setColorAdvertencia("#ffc107");
        config.setColorInfo("#17a2b8");
        config.setColorOscuro("#343a40");
        config.setColorClaro("#f8f9fa");
        config.setColorBronce("#cd7f32");
        repository.save(config);
    }

    public String generarCssPersonalizado() {
        Configuracion config = obtenerConfiguracion();
        return ":root {\n" +
                "  --color-primario: " + config.getColorPrimario() + ";\n" +
                "  --color-secundario: " + config.getColorSecundario() + ";\n" +
                "  --color-exito: " + config.getColorExito() + ";\n" +
                "  --color-peligro: " + config.getColorPeligro() + ";\n" +
                "  --color-advertencia: " + config.getColorAdvertencia() + ";\n" +
                "  --color-info: " + config.getColorInfo() + ";\n" +
                "  --color-oscuro: " + config.getColorOscuro() + ";\n" +
                "  --color-claro: " + config.getColorClaro() + ";\n" +
                "  --color-bronce: " + config.getColorBronce() + ";\n" +
                "}\n\n" +
                ".btn-primary { background-color: var(--color-primario) !important; border-color: var(--color-primario) !important; }\n" +
                ".btn-success { background-color: var(--color-exito) !important; border-color: var(--color-exito) !important; }\n" +
                ".btn-danger { background-color: var(--color-peligro) !important; border-color: var(--color-peligro) !important; }\n" +
                ".btn-warning { background-color: var(--color-advertencia) !important; border-color: var(--color-advertencia) !important; }\n" +
                ".btn-info { background-color: var(--color-info) !important; border-color: var(--color-info) !important; }\n" +
                ".bg-primary { background-color: var(--color-primario) !important; }\n" +
                ".bg-success { background-color: var(--color-exito) !important; }\n" +
                ".bg-danger { background-color: var(--color-peligro) !important; }\n" +
                ".bg-warning { background-color: var(--color-advertencia) !important; }\n" +
                ".bg-info { background-color: var(--color-info) !important; }\n" +
                ".text-primary { color: var(--color-primario) !important; }\n" +
                ".badge-primary { background-color: var(--color-primario) !important; }\n" +
                ".badge-success { background-color: var(--color-exito) !important; }\n" +
                ".badge-danger { background-color: var(--color-peligro) !important; }\n" +
                ".badge-warning { background-color: var(--color-advertencia) !important; }\n" +
                ".badge-info { background-color: var(--color-info) !important; }\n";
    }
}