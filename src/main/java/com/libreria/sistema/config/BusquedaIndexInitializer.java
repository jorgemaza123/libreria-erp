package com.libreria.sistema.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Inicializador de índices para el Omnibuscador.
 * Crea automáticamente los índices necesarios para búsquedas eficientes
 * si no existen en la base de datos.
 *
 * Requiere la extensión pg_trgm de PostgreSQL para índices trigram.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BusquedaIndexInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void inicializarIndices() {
        log.info("Verificando índices de búsqueda...");

        try {
            // 1. Intentar crear la extensión pg_trgm
            crearExtension("pg_trgm");

            // 2. Crear índices trigram si la extensión está disponible
            if (existeExtension("pg_trgm")) {
                crearIndiceTrigram("idx_productos_nombre_trgm", "productos", "nombre");
                crearIndiceTrigram("idx_productos_marca_trgm", "productos", "marca");
                crearIndiceTrigram("idx_productos_categoria_trgm", "productos", "categoria");
                log.info("Índices trigram configurados correctamente");
            } else {
                log.warn("Extensión pg_trgm no disponible. Las búsquedas funcionarán pero más lento.");
                log.warn("Para instalar: CREATE EXTENSION pg_trgm; (requiere permisos de superusuario)");
            }

            // 3. Crear índices B-tree (siempre funcionan)
            crearIndiceBtree("idx_productos_codigo_barra", "productos", "codigo_barra");
            crearIndiceBtree("idx_productos_codigo_interno", "productos", "codigo_interno");
            crearIndiceCompuesto("idx_productos_activo_stock", "productos", "activo, stock_actual DESC");

            log.info("Índices de búsqueda verificados correctamente");

        } catch (Exception e) {
            // No fallar si no se pueden crear índices - las búsquedas seguirán funcionando
            log.warn("No se pudieron crear algunos índices de búsqueda: {}. Las búsquedas funcionarán pero podrían ser más lentas.", e.getMessage());
        }
    }

    private void crearExtension(String nombreExtension) {
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS " + nombreExtension);
            log.debug("Extensión {} verificada/creada", nombreExtension);
        } catch (Exception e) {
            log.debug("No se pudo crear extensión {}: {} (puede requerir permisos de superusuario)", nombreExtension, e.getMessage());
        }
    }

    private boolean existeExtension(String nombreExtension) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_extension WHERE extname = ?",
                    Integer.class,
                    nombreExtension
            );
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void crearIndiceTrigram(String nombreIndice, String tabla, String columna) {
        if (!existeIndice(nombreIndice)) {
            try {
                String sql = String.format(
                        "CREATE INDEX %s ON %s USING gin (%s gin_trgm_ops)",
                        nombreIndice, tabla, columna
                );
                jdbcTemplate.execute(sql);
                log.debug("Índice trigram creado: {}", nombreIndice);
            } catch (Exception e) {
                log.debug("No se pudo crear índice {}: {}", nombreIndice, e.getMessage());
            }
        }
    }

    private void crearIndiceBtree(String nombreIndice, String tabla, String columna) {
        if (!existeIndice(nombreIndice)) {
            try {
                String sql = String.format(
                        "CREATE INDEX IF NOT EXISTS %s ON %s (%s)",
                        nombreIndice, tabla, columna
                );
                jdbcTemplate.execute(sql);
                log.debug("Índice B-tree creado: {}", nombreIndice);
            } catch (Exception e) {
                log.debug("No se pudo crear índice {}: {}", nombreIndice, e.getMessage());
            }
        }
    }

    private void crearIndiceCompuesto(String nombreIndice, String tabla, String columnas) {
        if (!existeIndice(nombreIndice)) {
            try {
                String sql = String.format(
                        "CREATE INDEX IF NOT EXISTS %s ON %s (%s)",
                        nombreIndice, tabla, columnas
                );
                jdbcTemplate.execute(sql);
                log.debug("Índice compuesto creado: {}", nombreIndice);
            } catch (Exception e) {
                log.debug("No se pudo crear índice {}: {}", nombreIndice, e.getMessage());
            }
        }
    }

    private boolean existeIndice(String nombreIndice) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes WHERE indexname = ?",
                    Integer.class,
                    nombreIndice
            );
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
