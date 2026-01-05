-- ============================================
-- DATOS DE PRUEBA - SISTEMA LIBRERIA
-- Base de datos: libreria_db
-- Generado: 2026-01-04
-- ============================================

-- ============================================
-- 1. CLIENTES (5 registros)
-- ============================================

-- Cliente 1: Persona Natural con DNI
INSERT INTO clientes (tipo_documento, numero_documento, nombre_razon_social, direccion, telefono, email, tiene_credito, dias_credito, fecha_registro)
VALUES ('1', '72345678', 'Juan Carlos Pérez Sánchez', 'Av. Los Jardines 234, San Isidro, Lima', '987654321', 'jcperez@gmail.com', false, 0, NOW());

-- Cliente 2: Persona Natural con DNI y crédito
INSERT INTO clientes (tipo_documento, numero_documento, nombre_razon_social, direccion, telefono, email, tiene_credito, dias_credito, fecha_registro)
VALUES ('1', '45678912', 'María Elena Rodríguez Torres', 'Jr. Las Flores 567, Miraflores, Lima', '965432178', 'mrodriguez@hotmail.com', true, 15, NOW());

-- Cliente 3: Empresa con RUC
INSERT INTO clientes (tipo_documento, numero_documento, nombre_razon_social, direccion, telefono, email, tiene_credito, dias_credito, fecha_registro)
VALUES ('6', '20456789123', 'CORPORACION EDUCATIVA SAC', 'Av. Universitaria 1245, Los Olivos, Lima', '014567890', 'ventas@corpedu.com.pe', true, 30, NOW());

-- Cliente 4: Empresa con RUC y crédito corto
INSERT INTO clientes (tipo_documento, numero_documento, nombre_razon_social, direccion, telefono, email, tiene_credito, dias_credito, fecha_registro)
VALUES ('6', '20567891234', 'DISTRIBUIDORA ESCOLAR EIRL', 'Jr. Comercio 890, Cercado de Lima', '013456789', 'admin@distescolar.pe', true, 7, NOW());

-- Cliente 5: Persona Natural sin crédito
INSERT INTO clientes (tipo_documento, numero_documento, nombre_razon_social, direccion, telefono, email, tiene_credito, dias_credito, fecha_registro)
VALUES ('1', '38765432', 'Pedro Antonio Flores Vega', 'Calle Los Pinos 432, Surco, Lima', '998765432', 'pflores@yahoo.com', false, 0, NOW());


-- ============================================
-- 2. PROVEEDORES (5 registros)
-- ============================================

-- Proveedor 1: Distribuidor de útiles escolares
INSERT INTO proveedores (ruc, razon_social, direccion, telefono, email, contacto, activo, fecha_registro)
VALUES ('20123456789', 'DISTRIBUIDORA ATLAS SAC', 'Av. Argentina 1234, Callao', '014567123', 'ventas@atlas.com.pe', 'Roberto Mendoza', true, NOW());

-- Proveedor 2: Importador de artículos de oficina
INSERT INTO proveedores (ruc, razon_social, direccion, telefono, email, contacto, activo, fecha_registro)
VALUES ('20234567891', 'IMPORTACIONES OFFICE PERU SA', 'Jr. Paruro 567, Cercado de Lima', '014231567', 'compras@officeperu.pe', 'Carmen Ríos', true, NOW());

-- Proveedor 3: Editorial de libros
INSERT INTO proveedores (ruc, razon_social, direccion, telefono, email, contacto, activo, fecha_registro)
VALUES ('20345678912', 'EDITORIAL HORIZONTE EIRL', 'Av. Petit Thouars 890, Lince', '012345678', 'editorial@horizonte.com', 'Luis Fernández', true, NOW());

-- Proveedor 4: Proveedor de tecnología
INSERT INTO proveedores (ruc, razon_social, direccion, telefono, email, contacto, activo, fecha_registro)
VALUES ('20456789123', 'TECH SUPPLIES PERU SAC', 'Av. Aviación 2345, San Borja', '016789012', 'ventas@techsupplies.pe', 'Ana García', true, NOW());

-- Proveedor 5: Mayorista de papelería
INSERT INTO proveedores (ruc, razon_social, direccion, telefono, email, contacto, activo, fecha_registro)
VALUES ('20567891234', 'PAPELERIA MAYORISTA DEL SUR SA', 'Av. Bolognesi 678, Arequipa', '054123456', 'mayorista@pmsur.com.pe', 'Jorge Delgado', true, NOW());


-- ============================================
-- 3. PRODUCTOS (5 registros)
-- ============================================

-- Producto 1: Cuaderno Escolar
INSERT INTO productos (codigo_barra, codigo_interno, nombre, categoria, descripcion, precio_compra, precio_venta, precio_mayorista, stock_actual, stock_minimo, unidad_medida, ubicacion_fila, ubicacion_columna, ubicacion_estante, tipo_afectacion_igv, activo, fecha_creacion, marca, modelo, color, generacion, tipo)
VALUES ('7501234567890', 'CUA-001', 'Cuaderno Escolar Rayado 100 Hojas', 'ÚTILES ESCOLARES', 'Cuaderno tamaño A4, rayado, pasta dura, 100 hojas', 3.50, 6.00, 5.20, 150, 20, 'UNIDAD', 'A', '3', '2', 'GRAVADO', true, NOW(), 'Atlas', 'Profesional', 'Azul', '2024', 'Escolar');

-- Producto 2: Lapicero Azul
INSERT INTO productos (codigo_barra, codigo_interno, nombre, categoria, descripcion, precio_compra, precio_venta, precio_mayorista, stock_actual, stock_minimo, unidad_medida, ubicacion_fila, ubicacion_columna, ubicacion_estante, tipo_afectacion_igv, activo, fecha_creacion, marca, modelo, color, generacion, tipo)
VALUES ('7502345678901', 'LAP-001', 'Lapicero Punta Fina 0.5mm Azul', 'ÚTILES DE ESCRITURA', 'Lapicero de tinta gel, punta fina 0.5mm, color azul', 0.80, 1.50, 1.20, 500, 50, 'UNIDAD', 'B', '1', '1', 'GRAVADO', true, NOW(), 'Pilot', 'G2', 'Azul', '2024', 'Escritura');

-- Producto 3: Resma de Papel Bond A4
INSERT INTO productos (codigo_barra, codigo_interno, nombre, categoria, descripcion, precio_compra, precio_venta, precio_mayorista, stock_actual, stock_minimo, unidad_medida, ubicacion_fila, ubicacion_columna, ubicacion_estante, tipo_afectacion_igv, activo, fecha_creacion, marca, modelo, color, generacion, tipo)
VALUES ('7503456789012', 'PAP-001', 'Resma Papel Bond A4 75gr 500 Hojas', 'PAPELERÍA', 'Papel bond blanco, tamaño A4, gramaje 75gr, paquete de 500 hojas', 12.50, 22.00, 18.50, 80, 10, 'PAQUETE', 'C', '2', '3', 'GRAVADO', true, NOW(), 'Chamex', 'Premium', 'Blanco', '2024', 'Oficina');

-- Producto 4: Archivador Palanca
INSERT INTO productos (codigo_barra, codigo_interno, nombre, categoria, descripcion, precio_compra, precio_venta, precio_mayorista, stock_actual, stock_minimo, unidad_medida, ubicacion_fila, ubicacion_columna, ubicacion_estante, tipo_afectacion_igv, activo, fecha_creacion, marca, modelo, color, generacion, tipo)
VALUES ('7504567890123', 'ARC-001', 'Archivador Palanca Lomo Ancho A4', 'ARCHIVO Y ORGANIZACIÓN', 'Archivador de palanca, lomo ancho 7.5cm, tamaño A4, cartón forrado', 8.00, 14.50, 12.00, 60, 10, 'UNIDAD', 'A', '5', '2', 'GRAVADO', true, NOW(), 'Artesco', 'Ejecutivo', 'Negro', '2024', 'Oficina');

-- Producto 5: Calculadora Científica
INSERT INTO productos (codigo_barra, codigo_interno, nombre, categoria, descripcion, precio_compra, precio_venta, precio_mayorista, stock_actual, stock_minimo, unidad_medida, ubicacion_fila, ubicacion_columna, ubicacion_estante, tipo_afectacion_igv, activo, fecha_creacion, marca, modelo, color, generacion, tipo)
VALUES ('7505678901234', 'CAL-001', 'Calculadora Científica 240 Funciones', 'ELECTRÓNICA', 'Calculadora científica, 240 funciones, pantalla LCD 2 líneas, con tapa protectora', 25.00, 45.00, 38.00, 35, 5, 'UNIDAD', 'D', '1', '1', 'GRAVADO', true, NOW(), 'Casio', 'FX-82MS', 'Gris', '2nd', 'Científica');


-- ============================================
-- 4. PRODUCTOS ADICIONALES (5 más)
-- ============================================

-- Producto 6: Mochila Escolar
INSERT INTO productos (codigo_barra, codigo_interno, nombre, categoria, descripcion, precio_compra, precio_venta, precio_mayorista, stock_actual, stock_minimo, unidad_medida, ubicacion_fila, ubicacion_columna, ubicacion_estante, tipo_afectacion_igv, activo, fecha_creacion, marca, modelo, color, generacion, tipo)
VALUES ('7506789012345', 'MOC-001', 'Mochila Escolar Reforzada 2 Compartimientos', 'ACCESORIOS', 'Mochila escolar con espaldar acolchado, 2 compartimientos, porta laptop', 35.00, 65.00, 55.00, 40, 8, 'UNIDAD', 'E', '4', '2', 'GRAVADO', true, NOW(), 'Totto', 'Acuarela', 'Multicolor', '2024', 'Escolar');

-- Producto 7: Corrector Líquido
INSERT INTO productos (codigo_barra, codigo_interno, nombre, categoria, descripcion, precio_compra, precio_venta, precio_mayorista, stock_actual, stock_minimo, unidad_medida, ubicacion_fila, ubicacion_columna, ubicacion_estante, tipo_afectacion_igv, activo, fecha_creacion, marca, modelo, color, generacion, tipo)
VALUES ('7507890123456', 'COR-001', 'Corrector Líquido Secado Rápido 20ml', 'ÚTILES DE ESCRITURA', 'Corrector líquido de secado rápido, contenido 20ml, con aplicador de precisión', 1.50, 3.00, 2.50, 200, 30, 'UNIDAD', 'B', '2', '1', 'GRAVADO', true, NOW(), 'Liquid Paper', 'Fast Dry', 'Blanco', '2024', 'Corrección');

-- Producto 8: Tijera Escolar
INSERT INTO productos (codigo_barra, codigo_interno, nombre, categoria, descripcion, precio_compra, precio_venta, precio_mayorista, stock_actual, stock_minimo, unidad_medida, ubicacion_fila, ubicacion_columna, ubicacion_estante, tipo_afectacion_igv, activo, fecha_creacion, marca, modelo, color, generacion, tipo)
VALUES ('7508901234567', 'TIJ-001', 'Tijera Escolar Punta Roma 5 Pulgadas', 'ÚTILES ESCOLARES', 'Tijera de acero inoxidable, punta roma para seguridad, mango ergonómico', 2.50, 5.00, 4.20, 120, 25, 'UNIDAD', 'A', '2', '1', 'GRAVADO', true, NOW(), 'Faber-Castell', 'Kids', 'Rojo', '2024', 'Escolar');

-- Producto 9: Marcador Permanente
INSERT INTO productos (codigo_barra, codigo_interno, nombre, categoria, descripcion, precio_compra, precio_venta, precio_mayorista, stock_actual, stock_minimo, unidad_medida, ubicacion_fila, ubicacion_columna, ubicacion_estante, tipo_afectacion_igv, activo, fecha_creacion, marca, modelo, color, generacion, tipo)
VALUES ('7509012345678', 'MAR-001', 'Marcador Permanente Punta Redonda Negro', 'ÚTILES DE ESCRITURA', 'Marcador permanente de tinta indeleble, punta redonda media, color negro', 1.80, 3.50, 3.00, 180, 35, 'UNIDAD', 'B', '3', '1', 'GRAVADO', true, NOW(), 'Sharpie', 'Classic', 'Negro', '2024', 'Marcadores');

-- Producto 10: Pegamento en Barra
INSERT INTO productos (codigo_barra, codigo_interno, nombre, categoria, descripcion, precio_compra, precio_venta, precio_mayorista, stock_actual, stock_minimo, unidad_medida, ubicacion_fila, ubicacion_columna, ubicacion_estante, tipo_afectacion_igv, activo, fecha_creacion, marca, modelo, color, generacion, tipo)
VALUES ('7510123456789', 'PEG-001', 'Pegamento en Barra 40gr No Tóxico', 'ADHESIVOS', 'Pegamento en barra, contenido 40gr, lavable, no tóxico, ideal para papel', 1.20, 2.50, 2.00, 250, 40, 'UNIDAD', 'B', '4', '1', 'GRAVADO', true, NOW(), 'UHU', 'Stic', 'Blanco', '2024', 'Adhesivos');


-- ============================================
-- 5. CLIENTES ADICIONALES (5 más)
-- ============================================

-- Cliente 6: Institución Educativa
INSERT INTO clientes (tipo_documento, numero_documento, nombre_razon_social, direccion, telefono, email, tiene_credito, dias_credito, fecha_registro)
VALUES ('6', '20678912345', 'INSTITUCION EDUCATIVA SANTA ROSA SA', 'Av. La Marina 3456, San Miguel, Lima', '015678901', 'compras@iesantarosa.edu.pe', true, 45, NOW());

-- Cliente 7: Persona Natural joven
INSERT INTO clientes (tipo_documento, numero_documento, nombre_razon_social, direccion, telefono, email, tiene_credito, dias_credito, fecha_registro)
VALUES ('1', '76543210', 'Sofía Gabriela Mendoza Ruiz', 'Jr. Las Palmeras 123, Jesús María, Lima', '912345678', 'sgmendoza@outlook.com', false, 0, NOW());

-- Cliente 8: Empresa mediana
INSERT INTO clientes (tipo_documento, numero_documento, nombre_razon_social, direccion, telefono, email, tiene_credito, dias_credito, fecha_registro)
VALUES ('6', '20789123456', 'CONSTRUCTORA LOS ANDES SAC', 'Av. Javier Prado 2890, San Isidro, Lima', '016234567', 'logistica@losandes.com.pe', true, 30, NOW());

-- Cliente 9: Persona con carnet de extranjería
INSERT INTO clientes (tipo_documento, numero_documento, nombre_razon_social, direccion, telefono, email, tiene_credito, dias_credito, fecha_registro)
VALUES ('4', '001234567', 'Carlos Alberto Martínez González', 'Calle Los Cedros 789, Barranco, Lima', '923456789', 'cmartinez@gmail.com', false, 0, NOW());

-- Cliente 10: Microempresa
INSERT INTO clientes (tipo_documento, numero_documento, nombre_razon_social, direccion, telefono, email, tiene_credito, dias_credito, fecha_registro)
VALUES ('6', '20891234567', 'BAZAR Y LIBRERIA EL ESTUDIANTE EIRL', 'Jr. Huancayo 456, Breña, Lima', '014123456', 'ventas@elestudiante.pe', true, 15, NOW());


-- ============================================
-- 6. PROVEEDORES ADICIONALES (5 más)
-- ============================================

-- Proveedor 6: Distribuidor de mochilas
INSERT INTO proveedores (ruc, razon_social, direccion, telefono, email, contacto, activo, fecha_registro)
VALUES ('20678912345', 'IMPORTADORA DE MOCHILAS Y MALETAS SA', 'Av. Grau 1567, La Victoria, Lima', '014567234', 'ventas@mochilas.pe', 'Patricia Vargas', true, NOW());

-- Proveedor 7: Proveedor de tintas y toners
INSERT INTO proveedores (ruc, razon_social, direccion, telefono, email, contacto, activo, fecha_registro)
VALUES ('20789123456', 'SUMINISTROS DE IMPRESION TOTAL SAC', 'Jr. Cusco 234, Cercado de Lima', '013456234', 'admin@totalimpresion.com', 'Ricardo Salazar', true, NOW());

-- Proveedor 8: Editorial universitaria
INSERT INTO proveedores (ruc, razon_social, direccion, telefono, email, contacto, activo, fecha_registro)
VALUES ('20891234567', 'FONDO EDITORIAL UNIVERSITARIO EIRL', 'Av. Universitaria 3456, Pueblo Libre', '014234123', 'editorial@feu.edu.pe', 'Dra. Laura Campos', true, NOW());

-- Proveedor 9: Importador asiático
INSERT INTO proveedores (ruc, razon_social, direccion, telefono, email, contacto, activo, fecha_registro)
VALUES ('20912345678', 'ASIAN TRADE PERU SA', 'Av. Colonial 5678, Callao', '014789123', 'import@asiantrade.pe', 'Wang Li', true, NOW());

-- Proveedor 10: Fabricante nacional
INSERT INTO proveedores (ruc, razon_social, direccion, telefono, email, contacto, activo, fecha_registro)
VALUES ('20123456780', 'FABRICA NACIONAL DE UTILES SAC', 'Av. Industrial 890, Villa El Salvador', '012987456', 'fabricacion@fnautil.com.pe', 'Ing. José Huamán', true, NOW());


-- ============================================
-- NOTAS DE USO:
-- ============================================
-- 1. Ejecutar estos queries en orden
-- 2. Verificar que la base de datos 'libreria_db' esté activa
-- 3. Los campos fecha_registro y fecha_creacion usan NOW() para timestamp actual
-- 4. Los códigos de barra son ficticios pero siguen formato estándar
-- 5. Los precios incluyen IGV según normativa peruana
-- 6. Tipos de documento SUNAT: 1=DNI, 6=RUC, 4=Carnet Extranjería
-- 7. Tipo afectación IGV: GRAVADO (incluye impuesto)
--
-- Para ejecutar en PostgreSQL:
-- psql -U postgres -d libreria_db -f datos_prueba.sql
-- ============================================
