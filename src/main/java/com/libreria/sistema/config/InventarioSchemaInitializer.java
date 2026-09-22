package com.libreria.sistema.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Completa columnas de inventario agregadas despues de que la base ya existe.
 * Esto evita errores en instalaciones Docker con tablas antiguas de PostgreSQL.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventarioSchemaInitializer {

    private static final String TABLA_DETALLE = "detalle_toma_inventario";

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void verificarEsquemaInventario() {
        try {
            if (!existeTabla(TABLA_DETALLE)) {
                log.debug("Tabla {} aun no existe; Hibernate la creara si corresponde.", TABLA_DETALLE);
                return;
            }

            asegurarColumnasDetalleTomaInventario();
            normalizarBooleanosDetalleTomaInventario();
            log.info("Esquema de inventario rapido verificado correctamente");
        } catch (Exception e) {
            log.warn("No se pudo verificar el esquema de inventario rapido: {}", e.getMessage());
        }
    }

    private void asegurarColumnasDetalleTomaInventario() {
        agregarColumna("usuario_conteo VARCHAR(80)");
        agregarColumna("primer_conteo_fisico INTEGER");
        agregarColumna("segundo_conteo_fisico INTEGER");
        agregarColumna("fecha_primer_conteo TIMESTAMP");
        agregarColumna("fecha_segundo_conteo TIMESTAMP");
        agregarColumna("usuario_primer_conteo VARCHAR(80)");
        agregarColumna("usuario_segundo_conteo VARCHAR(80)");
        agregarColumna("segundo_conteo_requerido BOOLEAN DEFAULT FALSE");
        agregarColumna("segundo_conteo_confirmado BOOLEAN DEFAULT FALSE");
        agregarColumna("usuario_deshacer_conteo VARCHAR(80)");
        agregarColumna("fecha_deshacer_conteo TIMESTAMP");
        agregarColumna("zona_conteo VARCHAR(80)");
        agregarColumna("nombre_corregido VARCHAR(160)");
        agregarColumna("categoria_corregida VARCHAR(120)");
        agregarColumna("marca_corregida VARCHAR(80)");
        agregarColumna("color_corregido VARCHAR(80)");
        agregarColumna("modelo_corregido VARCHAR(80)");
        agregarColumna("tipo_corregido VARCHAR(60)");
        agregarColumna("clasificacion_corregida VARCHAR(40)");
        agregarColumna("costo_corregido NUMERIC(12,4)");
        agregarColumna("precio_corregido NUMERIC(12,2)");
        agregarColumna("correccion_pendiente BOOLEAN DEFAULT FALSE");
        agregarColumna("correccion_aplicada BOOLEAN DEFAULT FALSE");
        agregarColumna("fecha_correccion TIMESTAMP");
        agregarColumna("usuario_correccion VARCHAR(80)");
        agregarColumna("fecha_aplicacion_correccion TIMESTAMP");
        agregarColumna("usuario_aplicacion_correccion VARCHAR(80)");
        agregarColumna("costo_anterior_correccion NUMERIC(12,4)");
        agregarColumna("costo_aplicado_correccion NUMERIC(12,4)");
        agregarColumna("precio_anterior_correccion NUMERIC(12,2)");
        agregarColumna("precio_aplicado_correccion NUMERIC(12,2)");
        agregarColumna("stock_anterior_ajuste INTEGER");
        agregarColumna("stock_nuevo_ajuste INTEGER");
    }

    private void normalizarBooleanosDetalleTomaInventario() {
        normalizarBooleano("contado");
        normalizarBooleano("ajuste_aplicado");
        normalizarBooleano("segundo_conteo_requerido");
        normalizarBooleano("segundo_conteo_confirmado");
        normalizarBooleano("correccion_pendiente");
        normalizarBooleano("correccion_aplicada");
    }

    private void agregarColumna(String definicionColumna) {
        jdbcTemplate.execute("ALTER TABLE " + TABLA_DETALLE + " ADD COLUMN IF NOT EXISTS " + definicionColumna);
    }

    private void normalizarBooleano(String columna) {
        jdbcTemplate.update("UPDATE " + TABLA_DETALLE + " SET " + columna + " = FALSE WHERE " + columna + " IS NULL");
    }

    private boolean existeTabla(String nombreTabla) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE lower(table_name) = lower(?)
                """, Integer.class, nombreTabla);
        return count != null && count > 0;
    }
}
