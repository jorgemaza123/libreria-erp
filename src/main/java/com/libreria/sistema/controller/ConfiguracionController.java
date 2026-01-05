package com.libreria.sistema.controller;

import com.libreria.sistema.model.Configuracion;
import com.libreria.sistema.service.ConfiguracionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;
import java.util.Base64;

@Controller
@RequestMapping("/configuracion")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class ConfiguracionController {

    private final ConfiguracionService configuracionService;

    public ConfiguracionController(ConfiguracionService configuracionService) {
        this.configuracionService = configuracionService;
    }

    @GetMapping
    public String index() {
        return "redirect:/configuracion/general";
    }

    @GetMapping("/general")
    public String general(Model model) {
        model.addAttribute("config", configuracionService.obtenerConfiguracion());
        return "configuracion/general";
    }

    @PostMapping("/general/guardar")
    public String guardarGeneral(@ModelAttribute Configuracion configForm,
                                 @RequestParam(value = "fileLogo", required = false) MultipartFile fileLogo,
                                 RedirectAttributes attributes) {
        try {
            System.out.println("================ INICIO DEBUG GUARDADO ==================");
            System.out.println("1. Datos recibidos del formulario (configForm):");
            System.out.println(" - Nombre Empresa: " + configForm.getNombreEmpresa());
            System.out.println(" - Color Primario: " + configForm.getColorPrimario());
            System.out.println(" - RUC: " + configForm.getRuc());
            
            // 1. OBTENER LA CONFIGURACIÓN REAL DE LA BASE DE DATOS
            Configuracion configBD = configuracionService.obtenerConfiguracion();
            System.out.println("2. Datos actuales en Base de Datos (configBD):");
            System.out.println(" - ID: " + configBD.getId());
            System.out.println(" - Color Primario Actual: " + configBD.getColorPrimario());

            // 2. ACTUALIZAR MANUALMENTE CAMPO POR CAMPO
            
            // --- Datos de Empresa ---
            if(configForm.getNombreEmpresa() != null && !configForm.getNombreEmpresa().isEmpty()) {
                configBD.setNombreEmpresa(configForm.getNombreEmpresa());
            }
            if(configForm.getRuc() != null && !configForm.getRuc().isEmpty()) {
                configBD.setRuc(configForm.getRuc());
            }
            if(configForm.getDireccion() != null) configBD.setDireccion(configForm.getDireccion());
            if(configForm.getTelefono() != null) configBD.setTelefono(configForm.getTelefono());
            if(configForm.getEmail() != null) configBD.setEmail(configForm.getEmail());
            if(configForm.getSlogan() != null) configBD.setSlogan(configForm.getSlogan());
            if(configForm.getWebSite() != null) configBD.setWebSite(configForm.getWebSite());
            if(configForm.getFacebook() != null) configBD.setFacebook(configForm.getFacebook());
            if(configForm.getInstagram() != null) configBD.setInstagram(configForm.getInstagram());
            if(configForm.getWhatsapp() != null) configBD.setWhatsapp(configForm.getWhatsapp());
            if(configForm.getHorarioAtencion() != null) configBD.setHorarioAtencion(configForm.getHorarioAtencion());

            // --- Colores ---
            // AQUI ESTA LA CLAVE: Verificamos si entra a los IFs
            if (configForm.getColorPrimario() != null) {
                System.out.println(" -> Actualizando Color Primario a: " + configForm.getColorPrimario());
                configBD.setColorPrimario(configForm.getColorPrimario());
            }
            if (configForm.getColorSecundario() != null) configBD.setColorSecundario(configForm.getColorSecundario());
            if (configForm.getColorExito() != null) configBD.setColorExito(configForm.getColorExito());
            if (configForm.getColorPeligro() != null) configBD.setColorPeligro(configForm.getColorPeligro());
            if (configForm.getColorAdvertencia() != null) configBD.setColorAdvertencia(configForm.getColorAdvertencia());
            if (configForm.getColorInfo() != null) configBD.setColorInfo(configForm.getColorInfo());
            if (configForm.getColorBronce() != null) configBD.setColorBronce(configForm.getColorBronce());
            
            // --- Reportes ---
            if(configForm.getEncabezadoReportes() != null) configBD.setEncabezadoReportes(configForm.getEncabezadoReportes());
            if(configForm.getPiePaginaReportes() != null) configBD.setPiePaginaReportes(configForm.getPiePaginaReportes());
            if(configForm.getFormatoFechaReportes() != null) configBD.setFormatoFechaReportes(configForm.getFormatoFechaReportes());
            if(configForm.getFormatoMoneda() != null) configBD.setFormatoMoneda(configForm.getFormatoMoneda());
            if(configForm.getMostrarLogoEnReportes() != null) configBD.setMostrarLogoEnReportes(configForm.getMostrarLogoEnReportes());

            // --- Sistema y Finanzas ---
            if(configForm.getItemsPorPagina() != null) configBD.setItemsPorPagina(configForm.getItemsPorPagina());
            if(configForm.getStockMinimo() != null) configBD.setStockMinimo(configForm.getStockMinimo());
            if(configForm.getIgvPorcentaje() != null) configBD.setIgvPorcentaje(configForm.getIgvPorcentaje());
            if(configForm.getLimiteEfectivoCaja() != null) configBD.setLimiteEfectivoCaja(configForm.getLimiteEfectivoCaja());
            
            // Booleans
            if(configForm.getAperturaCajaObligatoria() != null) configBD.setAperturaCajaObligatoria(configForm.getAperturaCajaObligatoria());
            if(configForm.getCierreCajaCiego() != null) configBD.setCierreCajaCiego(configForm.getCierreCajaCiego());
            if(configForm.getPermitirStockNegativo() != null) configBD.setPermitirStockNegativo(configForm.getPermitirStockNegativo());
            if(configForm.getPermitirVentaFraccionada() != null) configBD.setPermitirVentaFraccionada(configForm.getPermitirVentaFraccionada());
            if(configForm.getPreciosIncluyenImpuesto() != null) configBD.setPreciosIncluyenImpuesto(configForm.getPreciosIncluyenImpuesto());

            // --- Facturación ---
            if(configForm.getFacturacionEndpoint() != null) configBD.setFacturacionEndpoint(configForm.getFacturacionEndpoint());
            if(configForm.getFacturacionToken() != null && !configForm.getFacturacionToken().isEmpty()) {
                configBD.setFacturacionToken(configForm.getFacturacionToken());
            }
            if(configForm.getModoProduccion() != null) configBD.setModoProduccion(configForm.getModoProduccion());

            // 3. PROCESAR LOGO
            if (fileLogo != null && !fileLogo.isEmpty()) {
                System.out.println(" -> Procesando nuevo logo...");
                byte[] bytes = fileLogo.getBytes();
                String base64 = Base64.getEncoder().encodeToString(bytes);
                configBD.setLogoBase64(base64);
            }

            // 4. GUARDAR
            System.out.println("3. Intentando guardar en BD...");
            configuracionService.guardarConfiguracion(configBD);
            System.out.println("4. Guardado finalizado.");
            System.out.println("================ FIN DEBUG ==================");
            
            attributes.addFlashAttribute("success", "Configuración actualizada correctamente");
        } catch (Exception e) {
            e.printStackTrace();
            attributes.addFlashAttribute("error", "Error al guardar: " + e.getMessage());
        }
        return "redirect:/configuracion/general";
    }

    @PostMapping("/general/colores-default")
    @ResponseBody
    public ResponseEntity<String> restaurarColores() {
        try {
            configuracionService.restaurarColoresPorDefecto();
            return ResponseEntity.ok("Colores restaurados correctamente");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}