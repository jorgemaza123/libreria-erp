package com.libreria.sistema.controller;

import com.libreria.sistema.model.Amortizacion;
import com.libreria.sistema.model.Venta;
import com.libreria.sistema.repository.AmortizacionRepository;
import com.libreria.sistema.repository.VentaRepository;
import com.libreria.sistema.service.CajaService;
import com.libreria.sistema.service.ConfiguracionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/cobranzas")
@Slf4j
public class CobranzaController {

    private final VentaRepository ventaRepository;
    private final AmortizacionRepository amortizacionRepository;
    private final CajaService cajaService;
    private final ConfiguracionService configuracionService;

    public CobranzaController(VentaRepository ventaRepository, AmortizacionRepository amortizacionRepository, CajaService cajaService, ConfiguracionService configuracionService) {
        this.ventaRepository = ventaRepository;
        this.amortizacionRepository = amortizacionRepository;
        this.cajaService = cajaService;
        this.configuracionService = configuracionService;
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'COBRANZAS_VER')")
    public String index() {
        return "cobranzas/index";
    }

    @GetMapping("/buscar")
    @PreAuthorize("hasPermission(null, 'COBRANZAS_VER')")
    public String buscarDeudas(@RequestParam String dni, Model model) {
        List<Venta> deudas = ventaRepository.findDeudasPorDni(dni);
        model.addAttribute("deudas", deudas);
        model.addAttribute("dniBusqueda", dni);
        return "cobranzas/index";
    }

    @PostMapping("/pagar")
    @ResponseBody
    @Transactional
    @PreAuthorize("hasPermission(null, 'COBRANZAS_CREAR')")
    public ResponseEntity<?> registrarPago(@RequestParam Long ventaId,
                                           @RequestParam BigDecimal montoPago,
                                           @RequestParam(defaultValue = "EFECTIVO") String metodoPago) {
        try {
            Venta venta = ventaRepository.findById(ventaId)
                    .orElseThrow(() -> new RuntimeException("Venta no encontrada"));

            if (montoPago.compareTo(venta.getSaldoPendiente()) > 0) {
                return ResponseEntity.badRequest().body("El monto excede la deuda pendiente.");
            }

            Amortizacion pago = new Amortizacion();
            pago.setVenta(venta);
            pago.setMonto(montoPago);
            pago.setMetodoPago(metodoPago);
            pago.setObservacion("PAGO A CUENTA / CUOTA - " + metodoPago);
            Amortizacion pagoGuardado = amortizacionRepository.save(pago);

            venta.setMontoPagado(venta.getMontoPagado().add(montoPago));
            venta.setSaldoPendiente(venta.getSaldoPendiente().subtract(montoPago));

            if (venta.getSaldoPendiente().compareTo(BigDecimal.ZERO) == 0) {
                venta.setEstado("PAGADO_TOTAL");
            }
            ventaRepository.save(venta);

            // Registrar movimiento en caja - OBLIGATORIO: Si falla, debe abortar la transacción (ahora con @Transactional)
            cajaService.registrarMovimiento("INGRESO", "COBRO CUOTA " + venta.getSerie() + "-" + venta.getNumero() + " (" + metodoPago + ")", montoPago);

            // Devolvemos el ID del pago para que el JS abra el ticket
            return ResponseEntity.ok(Map.of(
                "id", pagoGuardado.getId(),
                "saldoRestante", venta.getSaldoPendiente()
            ));

        } catch (Exception e) {
            log.error("Error al registrar pago de cobranza", e);
            return ResponseEntity.badRequest().body("Error al procesar el pago. Por favor intente nuevamente.");
        }
    }

    @GetMapping("/ticket/{idAmortizacion}")
    @PreAuthorize("hasPermission(null, 'COBRANZAS_VER')")
    public String ticketPago(@PathVariable Long idAmortizacion, Model model) {
        Amortizacion pago = amortizacionRepository.findById(idAmortizacion)
                .orElseThrow(() -> new RuntimeException("Pago no encontrado"));
        
        model.addAttribute("pago", pago);
        model.addAttribute("venta", pago.getVenta());
        model.addAttribute("config", configuracionService.obtenerConfiguracion());
        return "cobranzas/ticket_pago";
    }
}