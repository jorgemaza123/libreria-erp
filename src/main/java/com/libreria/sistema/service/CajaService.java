package com.libreria.sistema.service;

import com.libreria.sistema.model.MovimientoCaja;
import com.libreria.sistema.repository.MovimientoCajaRepository;
import com.libreria.sistema.repository.UsuarioRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class CajaService {

    private final MovimientoCajaRepository cajaRepository;
    private final UsuarioRepository usuarioRepository;

    public CajaService(MovimientoCajaRepository cajaRepository, UsuarioRepository usuarioRepository) {
        this.cajaRepository = cajaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public List<MovimientoCaja> listarMovimientosHoy() {
        LocalDateTime inicioDia = LocalDate.now().atStartOfDay();
        return cajaRepository.findByFechaAfterOrderByFechaDesc(inicioDia);
    }

    public Map<String, BigDecimal> obtenerBalanceHoy() {
        LocalDateTime inicioDia = LocalDate.now().atStartOfDay();
        // Usamos COALESCE en SQL o manejamos nulls aquí para evitar errores si no hay movs
        BigDecimal ingresos = cajaRepository.sumarIngresos(inicioDia);
        BigDecimal egresos = cajaRepository.sumarEgresos(inicioDia);
        
        if (ingresos == null) ingresos = BigDecimal.ZERO;
        if (egresos == null) egresos = BigDecimal.ZERO;
        
        BigDecimal saldoHoy = ingresos.subtract(egresos);

        return Map.of(
            "ingresos", ingresos,
            "egresos", egresos,
            "saldoHoy", saldoHoy // Saldo solo de hoy
        );
    }

    // --- NUEVO: SALDO REAL EN CAJÓN (HISTÓRICO) ---
    public BigDecimal calcularSaldoTotal() {
        List<MovimientoCaja> todos = cajaRepository.findAll();
        BigDecimal saldo = BigDecimal.ZERO;
        for (MovimientoCaja m : todos) {
            if ("INGRESO".equals(m.getTipo())) {
                saldo = saldo.add(m.getMonto());
            } else {
                saldo = saldo.subtract(m.getMonto());
            }
        }
        return saldo;
    }

    @Transactional
    public void registrarMovimiento(String tipo, String concepto, BigDecimal monto) {
        MovimientoCaja mov = new MovimientoCaja();
        mov.setTipo(tipo);
        mov.setConcepto(concepto.toUpperCase());
        mov.setMonto(monto);
        
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            usuarioRepository.findByUsername(username).ifPresent(mov::setUsuario);
        } catch (Exception e) {
            // Usuario null si es automático
        }

        cajaRepository.save(mov);
    }
}