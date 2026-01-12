package com.libreria.sistema.controller;

import com.libreria.sistema.model.Cliente;
import com.libreria.sistema.service.ClienteService;
import com.libreria.sistema.service.ConsultaDocumentoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador de gestión de clientes.
 * Proporciona CRUD completo, gestión de créditos e historial.
 */
@Controller
@RequestMapping("/clientes")
@Slf4j
public class ClienteController {

    private final ClienteService clienteService;
    private final ConsultaDocumentoService consultaDocumentoService;

    public ClienteController(ClienteService clienteService, ConsultaDocumentoService consultaDocumentoService) {
        this.clienteService = clienteService;
        this.consultaDocumentoService = consultaDocumentoService;
    }

    // =====================================================
    //  VISTAS PRINCIPALES
    // =====================================================

    /**
     * Listado de clientes con búsqueda y paginación
     */
    @GetMapping
    @PreAuthorize("hasPermission(null, 'CLIENTES_VER')")
    public String listar(@RequestParam(defaultValue = "") String buscar,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "20") int size,
                         Model model) {

        Page<Cliente> clientes = clienteService.buscarPaginado(buscar, PageRequest.of(page, size));

        model.addAttribute("clientes", clientes);
        model.addAttribute("buscar", buscar);
        model.addAttribute("dashboard", clienteService.obtenerDashboard());
        model.addAttribute("active", "clientes");

        return "clientes/lista";
    }

    /**
     * Formulario para nuevo cliente
     */
    @GetMapping("/nuevo")
    @PreAuthorize("hasPermission(null, 'CLIENTES_CREAR')")
    public String nuevo(Model model) {
        Cliente cliente = new Cliente();
        cliente.setActivo(true);
        cliente.setTipoDocumento("1"); // DNI por defecto

        model.addAttribute("cliente", cliente);
        model.addAttribute("titulo", "Nuevo Cliente");
        model.addAttribute("categorias", clienteService.obtenerCategorias());
        return "clientes/formulario";
    }

    /**
     * Formulario para editar cliente existente
     */
    @GetMapping("/editar/{id}")
    @PreAuthorize("hasPermission(null, 'CLIENTES_EDITAR')")
    public String editar(@PathVariable Long id, Model model, RedirectAttributes attributes) {
        return clienteService.obtenerPorId(id).map(cliente -> {
            model.addAttribute("cliente", cliente);
            model.addAttribute("titulo", "Editar Cliente");
            model.addAttribute("categorias", clienteService.obtenerCategorias());
            return "clientes/formulario";
        }).orElseGet(() -> {
            attributes.addFlashAttribute("error", "Cliente no encontrado");
            return "redirect:/clientes";
        });
    }

    /**
     * Ver detalle de cliente con historial
     */
    @GetMapping("/ver/{id}")
    @PreAuthorize("hasPermission(null, 'CLIENTES_VER')")
    public String ver(@PathVariable Long id, Model model, RedirectAttributes attributes) {
        return clienteService.obtenerPorId(id).map(cliente -> {
            model.addAttribute("cliente", cliente);
            model.addAttribute("estadisticas", clienteService.obtenerEstadisticas(id));
            model.addAttribute("historialCompras", clienteService.obtenerHistorialCompras(id));
            return "clientes/detalle";
        }).orElseGet(() -> {
            attributes.addFlashAttribute("error", "Cliente no encontrado");
            return "redirect:/clientes";
        });
    }

    // =====================================================
    //  OPERACIONES CRUD
    // =====================================================

    /**
     * Guardar cliente (crear o actualizar)
     */
    @PostMapping("/guardar")
    @PreAuthorize("hasPermission(null, 'CLIENTES_CREAR') or hasPermission(null, 'CLIENTES_EDITAR')")
    public String guardar(@ModelAttribute Cliente cliente, RedirectAttributes attributes) {
        try {
            clienteService.guardar(cliente);
            attributes.addFlashAttribute("success", "Cliente guardado correctamente");
            return "redirect:/clientes";
        } catch (Exception e) {
            log.error("Error al guardar cliente: {}", e.getMessage());
            attributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/clientes/nuevo";
        }
    }

    /**
     * Eliminar (desactivar) cliente
     */
    @GetMapping("/eliminar/{id}")
    @PreAuthorize("hasPermission(null, 'CLIENTES_ELIMINAR')")
    public String eliminar(@PathVariable Long id, RedirectAttributes attributes) {
        try {
            clienteService.eliminar(id);
            attributes.addFlashAttribute("success", "Cliente desactivado correctamente");
        } catch (Exception e) {
            attributes.addFlashAttribute("error", "Error al desactivar: " + e.getMessage());
        }
        return "redirect:/clientes";
    }

    /**
     * Reactivar cliente
     */
    @GetMapping("/activar/{id}")
    @PreAuthorize("hasPermission(null, 'CLIENTES_EDITAR')")
    public String activar(@PathVariable Long id, RedirectAttributes attributes) {
        try {
            clienteService.activar(id);
            attributes.addFlashAttribute("success", "Cliente activado correctamente");
        } catch (Exception e) {
            attributes.addFlashAttribute("error", "Error al activar: " + e.getMessage());
        }
        return "redirect:/clientes";
    }

    // =====================================================
    //  GESTIÓN DE CRÉDITO
    // =====================================================

    /**
     * Vista de clientes con crédito
     */
    @GetMapping("/creditos")
    @PreAuthorize("hasPermission(null, 'CLIENTES_VER')")
    public String vistaCreditos(Model model) {
        model.addAttribute("clientesConCredito", clienteService.obtenerClientesConCredito());
        model.addAttribute("clientesConDeuda", clienteService.obtenerClientesConDeuda());
        model.addAttribute("clientesMorosos", clienteService.obtenerClientesMorosos());
        model.addAttribute("active", "clientes_creditos");
        return "clientes/creditos";
    }

    /**
     * Habilitar crédito a un cliente
     */
    @PostMapping("/habilitar-credito")
    @PreAuthorize("hasPermission(null, 'CLIENTES_EDITAR')")
    @ResponseBody
    public ResponseEntity<?> habilitarCredito(@RequestParam Long clienteId,
                                               @RequestParam BigDecimal limiteCredito,
                                               @RequestParam(defaultValue = "30") Integer diasCredito) {
        try {
            clienteService.habilitarCredito(clienteId, limiteCredito, diasCredito);
            return ResponseEntity.ok(Map.of("success", true, "message", "Crédito habilitado correctamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Deshabilitar crédito
     */
    @PostMapping("/deshabilitar-credito/{id}")
    @PreAuthorize("hasPermission(null, 'CLIENTES_EDITAR')")
    @ResponseBody
    public ResponseEntity<?> deshabilitarCredito(@PathVariable Long id) {
        try {
            clienteService.deshabilitarCredito(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Crédito deshabilitado"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // =====================================================
    //  API PARA AJAX/AUTOCOMPLETADO
    // =====================================================

    /**
     * Buscar clientes para autocompletado en ventas
     */
    @GetMapping("/api/buscar")
    @ResponseBody
    public List<Map<String, Object>> buscarApi(@RequestParam String term) {
        return clienteService.buscarParaAutocompletado(term).stream()
                .map(c -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", c.getId());
                    map.put("text", c.getNumeroDocumento() + " - " + c.getNombreRazonSocial());
                    map.put("documento", c.getNumeroDocumento());
                    map.put("nombre", c.getNombreRazonSocial());
                    map.put("direccion", c.getDireccion());
                    map.put("telefono", c.getTelefono());
                    map.put("email", c.getEmail());
                    map.put("tieneCredito", c.isTieneCredito());
                    map.put("creditoDisponible", c.getCreditoDisponible());
                    map.put("diasCredito", c.getDiasCredito());
                    return map;
                })
                .toList();
    }

    /**
     * Obtener cliente por documento
     */
    @GetMapping("/api/por-documento/{documento}")
    @ResponseBody
    public ResponseEntity<?> obtenerPorDocumento(@PathVariable String documento) {
        return clienteService.obtenerPorDocumento(documento)
                .map(c -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", c.getId());
                    map.put("documento", c.getNumeroDocumento());
                    map.put("nombre", c.getNombreRazonSocial());
                    map.put("direccion", c.getDireccion());
                    map.put("telefono", c.getTelefono());
                    map.put("email", c.getEmail());
                    map.put("tieneCredito", c.isTieneCredito());
                    map.put("creditoDisponible", c.getCreditoDisponible());
                    map.put("diasCredito", c.getDiasCredito());
                    map.put("categoria", c.getCategoria());
                    return ResponseEntity.ok(map);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Verificar si cliente puede recibir crédito
     */
    @GetMapping("/api/puede-credito")
    @ResponseBody
    public ResponseEntity<?> puedeRecibirCredito(@RequestParam Long clienteId,
                                                  @RequestParam BigDecimal monto) {
        boolean puede = clienteService.puedeRecibirCredito(clienteId, monto);
        return ResponseEntity.ok(Map.of(
                "puedeCredito", puede,
                "mensaje", puede ? "Cliente puede recibir crédito" : "Cliente excede límite de crédito"
        ));
    }

    /**
     * Obtener estadísticas de un cliente
     */
    @GetMapping("/api/estadisticas/{id}")
    @ResponseBody
    public ResponseEntity<?> obtenerEstadisticas(@PathVariable Long id) {
        Map<String, Object> stats = clienteService.obtenerEstadisticas(id);
        if (stats.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(stats);
    }

    // =====================================================
    //  CONSULTA SUNAT/RENIEC (APISUNAT)
    // =====================================================

    /**
     * Consultar RUC en SUNAT vía APISUNAT.
     * Solo para FACTURA - permite autocompletar datos del cliente.
     *
     * @param ruc Número de RUC (11 dígitos)
     * @return Datos del contribuyente o error
     */
    @GetMapping("/api/consultar-ruc/{ruc}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> consultarRuc(@PathVariable String ruc) {
        log.info("Consultando RUC: {}", ruc);
        Map<String, Object> resultado = consultaDocumentoService.consultarRuc(ruc);

        // Si encontró datos en APISUNAT, verificar si existe en BD local
        if (Boolean.TRUE.equals(resultado.get("success"))) {
            clienteService.obtenerPorDocumento(ruc).ifPresent(clienteLocal -> {
                resultado.put("clienteLocalId", clienteLocal.getId());
                resultado.put("existeEnBD", true);
                // Usar dirección local si APISUNAT no la devuelve completa
                if (clienteLocal.getDireccion() != null && !clienteLocal.getDireccion().isBlank()) {
                    resultado.putIfAbsent("direccion", clienteLocal.getDireccion());
                }
            });
        }

        return ResponseEntity.ok(resultado);
    }

    /**
     * Consultar DNI en RENIEC vía APISUNAT.
     *
     * @param dni Número de DNI (8 dígitos)
     * @return Datos de la persona o error
     */
    @GetMapping("/api/consultar-dni/{dni}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> consultarDni(@PathVariable String dni) {
        log.info("Consultando DNI: {}", dni);
        Map<String, Object> resultado = consultaDocumentoService.consultarDni(dni);

        // Si encontró datos, verificar si existe en BD local
        if (Boolean.TRUE.equals(resultado.get("success"))) {
            clienteService.obtenerPorDocumento(dni).ifPresent(clienteLocal -> {
                resultado.put("clienteLocalId", clienteLocal.getId());
                resultado.put("existeEnBD", true);
                // Usar dirección local ya que RENIEC no devuelve dirección
                if (clienteLocal.getDireccion() != null) {
                    resultado.put("direccion", clienteLocal.getDireccion());
                }
            });
        }

        return ResponseEntity.ok(resultado);
    }

    /**
     * Consultar documento automáticamente (DNI o RUC según longitud).
     * Endpoint genérico que detecta el tipo de documento.
     *
     * @param documento Número de documento (8 dígitos = DNI, 11 dígitos = RUC)
     * @return Datos del documento o error
     */
    @GetMapping("/api/consultar-documento/{documento}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> consultarDocumento(@PathVariable String documento) {
        log.info("Consultando documento: {}", documento);
        Map<String, Object> resultado = consultaDocumentoService.consultarDocumento(documento);

        // Si encontró datos, verificar si existe en BD local
        if (Boolean.TRUE.equals(resultado.get("success"))) {
            clienteService.obtenerPorDocumento(documento).ifPresent(clienteLocal -> {
                resultado.put("clienteLocalId", clienteLocal.getId());
                resultado.put("existeEnBD", true);
                if (clienteLocal.getDireccion() != null) {
                    resultado.putIfAbsent("direccion", clienteLocal.getDireccion());
                }
            });
        }

        return ResponseEntity.ok(resultado);
    }

    /**
     * Verificar si el servicio de consulta APISUNAT está disponible
     */
    @GetMapping("/api/consulta-disponible")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> consultaDisponible() {
        boolean disponible = consultaDocumentoService.isServicioDisponible();
        return ResponseEntity.ok(Map.of(
            "disponible", disponible,
            "mensaje", disponible ? "Servicio de consulta SUNAT/RENIEC disponible" :
                "Token de APISUNAT no configurado. Configure en Configuración > General > Facturación."
        ));
    }
}
