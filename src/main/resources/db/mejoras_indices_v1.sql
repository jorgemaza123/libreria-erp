-- =====================================================
-- SCRIPT DE MEJORAS DE BASE DE DATOS — SISTEMA LIBRERIA ERP
-- Generado: 2026-03-08
-- INSTRUCCIONES: Ejecutar una sola vez en PostgreSQL con el usuario dueño de la BD
-- NO renombra tablas ni columnas existentes — solo agrega índices y constraints
-- =====================================================

-- =====================================================
-- MEDIO-2 FIX: Índice único para sesiones de caja abiertas
-- Evita que dos sesiones del mismo usuario queden ABIERTA simultáneamente
-- (race condition al abrir caja desde dos pestañas del navegador)
-- =====================================================
CREATE UNIQUE INDEX IF NOT EXISTS uk_sesion_usuario_abierta
    ON sesiones_caja (usuario_id)
    WHERE estado = 'ABIERTA';

-- =====================================================
-- BAJO-7 FIX: Índices en columnas de búsqueda frecuente
-- Mejora sensiblemente el tiempo de respuesta en búsquedas por nombre,
-- fecha, código y por estado.
-- =====================================================

-- Búsqueda por nombre de producto (case-insensitive)
CREATE INDEX IF NOT EXISTS idx_producto_nombre
    ON productos (LOWER(nombre));

-- Búsqueda por código interno / SKU
CREATE INDEX IF NOT EXISTS idx_producto_codigo_interno
    ON productos (codigo_interno);

-- Búsqueda por código de barras
CREATE INDEX IF NOT EXISTS idx_producto_codigo_barra
    ON productos (codigo_barra);

-- Búsqueda de ventas por fecha
CREATE INDEX IF NOT EXISTS idx_venta_fecha
    ON ventas (fecha_emision DESC);

-- Ventas por estado (filtros de deudas, anulados, etc.)
CREATE INDEX IF NOT EXISTS idx_venta_estado
    ON ventas (estado);

-- Movimientos de caja por sesión (para cuadre de caja)
CREATE INDEX IF NOT EXISTS idx_movimiento_sesion
    ON movimientos_caja (sesion_id);

-- Kardex por producto y fecha (historial de movimientos)
CREATE INDEX IF NOT EXISTS idx_kardex_producto
    ON kardex (producto_id, fecha DESC);

-- Kardex por tipo de movimiento
CREATE INDEX IF NOT EXISTS idx_kardex_tipo
    ON kardex (tipo);

-- Ventas a crédito pendientes (para módulo de cobranzas)
CREATE INDEX IF NOT EXISTS idx_venta_credito_pendiente
    ON ventas (saldo_pendiente)
    WHERE saldo_pendiente > 0 AND estado != 'ANULADO';

-- =====================================================
-- VERIFICACIÓN: Listar índices creados en esta sesión
-- =====================================================
SELECT schemaname, tablename, indexname, indexdef
FROM pg_indexes
WHERE indexname IN (
    'uk_sesion_usuario_abierta',
    'idx_producto_nombre',
    'idx_producto_codigo_interno',
    'idx_producto_codigo_barra',
    'idx_venta_fecha',
    'idx_venta_estado',
    'idx_movimiento_sesion',
    'idx_kardex_producto',
    'idx_kardex_tipo',
    'idx_venta_credito_pendiente'
)
ORDER BY tablename, indexname;
