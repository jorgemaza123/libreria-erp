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
        BigDecimal ingresos = cajaRepository.sumarIngresos(inicioDia);
        BigDecimal egresos = cajaRepository.sumarEgresos(inicioDia);
        BigDecimal saldo = ingresos.subtract(egresos);

        return Map.of(
            "ingresos", ingresos,
            "egresos", egresos,
            "saldo", saldo
        );
    }

    @Transactional
    public void registrarMovimiento(String tipo, String concepto, BigDecimal monto) {
        MovimientoCaja mov = new MovimientoCaja();
        mov.setTipo(tipo);
        mov.setConcepto(concepto.toUpperCase());
        mov.setMonto(monto);
        
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        usuarioRepository.findByUsername(username).ifPresent(mov::setUsuario);

        cajaRepository.save(mov);
    }
}