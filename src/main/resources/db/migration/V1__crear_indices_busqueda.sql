-- =====================================================
-- ÍNDICES OPTIMIZADOS PARA OMNIBUSCADOR
-- =====================================================
-- Este script crea índices para mejorar el rendimiento
-- de las búsquedas ILIKE en PostgreSQL.
--
-- Ejecutar manualmente en la base de datos o
-- usar Flyway para migraciones automáticas.
-- =====================================================

-- Extensión para búsquedas más rápidas con LIKE/ILIKE
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Extensión para normalizar acentos (opcional pero recomendada)
CREATE EXTENSION IF NOT EXISTS unaccent;

-- =====================================================
-- ÍNDICES GIN TRIGRAM PARA BÚSQUEDAS DE TEXTO
-- =====================================================
-- Los índices trigram permiten usar ILIKE '%texto%' de forma eficiente

-- Índice en nombre del producto (campo más buscado)
CREATE INDEX IF NOT EXISTS idx_productos_nombre_trgm
ON productos USING gin (nombre gin_trgm_ops);

-- Índice en marca
CREATE INDEX IF NOT EXISTS idx_productos_marca_trgm
ON productos USING gin (marca gin_trgm_ops);

-- Índice en categoría
CREATE INDEX IF NOT EXISTS idx_productos_categoria_trgm
ON productos USING gin (categoria gin_trgm_ops);

-- Índice en descripción
CREATE INDEX IF NOT EXISTS idx_productos_descripcion_trgm
ON productos USING gin (descripcion gin_trgm_ops);

-- =====================================================
-- ÍNDICES BTREE PARA BÚSQUEDAS EXACTAS
-- =====================================================

-- Índice en código de barras (búsquedas exactas frecuentes)
CREATE INDEX IF NOT EXISTS idx_productos_codigo_barra
ON productos (codigo_barra);

-- Índice en código interno (SKU)
CREATE INDEX IF NOT EXISTS idx_productos_codigo_interno
ON productos (codigo_interno);

-- Índice compuesto para filtrado por activo y stock
CREATE INDEX IF NOT EXISTS idx_productos_activo_stock
ON productos (activo, stock_actual DESC);

-- =====================================================
-- ÍNDICE PARCIAL PARA PRODUCTOS ACTIVOS
-- =====================================================
-- Solo indexa productos activos (optimiza búsquedas del POS)

CREATE INDEX IF NOT EXISTS idx_productos_activos_nombre
ON productos (nombre)
WHERE activo = true;

-- =====================================================
-- VERIFICAR ÍNDICES CREADOS
-- =====================================================
-- Ejecutar esta consulta para verificar:
-- SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'productos';
