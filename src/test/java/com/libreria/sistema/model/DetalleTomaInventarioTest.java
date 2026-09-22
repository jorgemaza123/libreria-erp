package com.libreria.sistema.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetalleTomaInventarioTest {

    @Test
    void registraPrimerYSegundoConteoComoAuditoriaSeparada() {
        DetalleTomaInventario detalle = new DetalleTomaInventario();
        detalle.setStockSistema(30);

        detalle.registrarPrimerConteo(2, "admin", true);

        assertEquals(2, detalle.getPrimerConteoFisico());
        assertEquals("admin", detalle.getUsuarioPrimerConteo());
        assertTrue(detalle.getSegundoConteoRequerido());
        assertFalse(detalle.getSegundoConteoConfirmado());
        assertFalse(detalle.getContado());
        assertNull(detalle.getFechaConteo());

        detalle.registrarSegundoConteo(3, "vendedor");

        assertEquals(3, detalle.getSegundoConteoFisico());
        assertEquals("vendedor", detalle.getUsuarioSegundoConteo());
        assertEquals(3, detalle.getStockFisico());
        assertEquals(-27, detalle.getDiferencia());
        assertTrue(detalle.getSegundoConteoConfirmado());
        assertTrue(detalle.getContado());
        assertNotNull(detalle.getFechaConteo());
    }

    @Test
    void limpiarConteosEliminaPrimerYSegundoConteo() {
        DetalleTomaInventario detalle = new DetalleTomaInventario();
        detalle.setStockSistema(10);
        detalle.registrarPrimerConteo(0, "admin", true);
        detalle.registrarSegundoConteo(0, "admin");

        detalle.limpiarConteos();

        assertNull(detalle.getPrimerConteoFisico());
        assertNull(detalle.getSegundoConteoFisico());
        assertNull(detalle.getStockFisico());
        assertNull(detalle.getDiferencia());
        assertFalse(detalle.getContado());
        assertFalse(detalle.getSegundoConteoRequerido());
        assertFalse(detalle.getSegundoConteoConfirmado());
    }
}
