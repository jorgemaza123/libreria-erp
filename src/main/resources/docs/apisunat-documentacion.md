# Documentación APISUNAT - PSE (Proveedor de Servicios Electrónicos)

## Información General

**PSE**: APISUNAT
**Documentación oficial**: https://apisunat.pe/docs

## Endpoints

### Ambiente de Pruebas (Sandbox)
```
https://sandbox.apisunat.pe/api/v3/documents
```

### Ambiente de Producción
```
https://app.apisunat.pe/api/v3/documents
```

## Autenticación

### Headers Requeridos

```http
Authorization: Bearer {TOKEN}
Content-Type: application/json
```

**Nota**: El TOKEN se obtiene desde el panel de APISUNAT después de registrarse y activar el servicio.

## Estructura de Request

### Boleta / Factura - Venta al Contado

```json
{
  "documento": "boleta",
  "serie": "B001",
  "numero": 123,
  "fecha_de_emision": "2025-01-03",
  "moneda": "PEN",
  "tipo_operacion": "0101",
  "cliente_tipo_de_documento": "1",
  "cliente_numero_de_documento": "75413811",
  "cliente_denominacion": "NOMBRE CLIENTE",
  "cliente_direccion": "DIRECCION",
  "items": [
    {
      "unidad_de_medida": "NIU",
      "descripcion": "Producto",
      "cantidad": "1",
      "valor_unitario": "234.125457",
      "porcentaje_igv": "18",
      "codigo_tipo_afectacion_igv": "10",
      "nombre_tributo": "IGV"
    }
  ],
  "total": "276.27"
}
```

### Factura - Venta al Crédito

```json
{
  "documento": "factura",
  "serie": "F001",
  "numero": 45,
  "fecha_de_emision": "2025-01-03",
  "fecha_de_vencimiento": "2025-01-15",
  "moneda": "PEN",
  "tipo_operacion": "0101",
  "cliente_tipo_de_documento": "6",
  "cliente_numero_de_documento": "20123456789",
  "cliente_denominacion": "EMPRESA SAC",
  "cliente_direccion": "AV. EJEMPLO 123",
  "items": [
    {
      "unidad_de_medida": "NIU",
      "descripcion": "Servicio de consultoría",
      "cantidad": "2",
      "valor_unitario": "500.00",
      "porcentaje_igv": "18",
      "codigo_tipo_afectacion_igv": "10",
      "nombre_tributo": "IGV"
    }
  ],
  "cuotas": [
    {
      "importe": "590.00",
      "fecha_de_pago": "2025-01-10"
    },
    {
      "importe": "590.00",
      "fecha_de_pago": "2025-01-15"
    }
  ],
  "total": "1180.00"
}
```

## Estructura de Response

### Response Exitoso

```json
{
  "success": true,
  "message": "Comprobante emitido correctamente",
  "payload": {
    "estado": "ACEPTADO",
    "hash": "4HY34uR7a+Vc1Ax3/zrUk1Ng+tjTLwlqa9mRFysy15E=",
    "xml": "http://api.lucode.pe/20123456789-03-B001-123.xml",
    "cdr": "http://api.lucode.pe/R-20123456789-03-B001-123.xml",
    "pdf": {
      "ticket": "http://api.lucode.pe/pdf/ticket/20123456789-03-B001-123"
    }
  }
}
```

**Campos del Payload**:
- `estado`: Estado del comprobante ante SUNAT
  - `ACEPTADO`: Comprobante aceptado por SUNAT
  - `PENDIENTE`: En proceso de validación
  - `RECHAZADO`: Rechazado por SUNAT (revisar errores)
- `hash`: Hash del comprobante para validación
- `xml`: URL del archivo XML firmado
- `cdr`: URL del CDR (Constancia de Recepción) de SUNAT
- `pdf.ticket`: URL del PDF en formato ticket

### Response Error

```json
{
  "success": false,
  "message": "Descripción del error"
}
```

**Errores comunes**:
- Token inválido o expirado
- Número de documento del cliente inválido
- Serie o número de comprobante duplicado
- Datos incompletos o formato incorrecto

## Catálogos SUNAT

### Tipo de Documento (cliente_tipo_de_documento)

| Código | Descripción |
|--------|-------------|
| 0 | OTROS |
| 1 | DNI |
| 4 | CARNET DE EXTRANJERÍA |
| 6 | RUC |
| 7 | PASAPORTE |

### Tipo de Comprobante (documento)

| Valor | Descripción | Serie |
|-------|-------------|-------|
| boleta | Boleta de Venta | B001, B002, etc. |
| factura | Factura | F001, F002, etc. |
| nota_credito | Nota de Crédito | BC01, FC01, etc. |
| nota_debito | Nota de Débito | BD01, FD01, etc. |

### Código Tipo Afectación IGV (codigo_tipo_afectacion_igv)

| Código | Descripción | Uso |
|--------|-------------|-----|
| 10 | Gravado - Operación Onerosa | Productos/servicios con IGV (18%) |
| 20 | Exonerado - Operación Onerosa | Productos exonerados de IGV |
| 30 | Inafecto - Operación Onerosa | Productos inafectos al IGV |
| 11 | Gravado - Gratuitas | Muestras gratis, bonificaciones |
| 21 | Exonerado - Gratuitas | Muestras gratis exoneradas |
| 31 | Inafecto - Gratuitas | Muestras gratis inafectas |

### Unidad de Medida (unidad_de_medida)

| Código | Descripción |
|--------|-------------|
| NIU | Unidad |
| ZZ | Servicio |
| KGM | Kilogramo |
| MTR | Metro |
| LTR | Litro |
| CEN | Ciento |
| MIL | Millar |
| SET | Juego/Set |

### Tipo de Operación (tipo_operacion)

| Código | Descripción |
|--------|-------------|
| 0101 | Venta interna |
| 0200 | Exportación |

### Moneda (moneda)

| Código | Descripción |
|--------|-------------|
| PEN | Soles |
| USD | Dólares Americanos |
| EUR | Euros |

## Flujo de Integración

1. **Generar Venta en Sistema Local**
   - Crear registro de Venta con todos los datos
   - Asignar serie y número correlativo
   - Guardar en estado "PENDIENTE_ENVIO"

2. **Preparar Request a APISUNAT**
   - Mapear datos de Venta → JSON APISUNAT
   - Incluir TOKEN en header Authorization

3. **Enviar Request POST**
   - Endpoint: `/api/v3/documents`
   - Timeout recomendado: 30 segundos

4. **Procesar Response**
   - Si `success = true`:
     - Guardar `hash`, `xml`, `cdr`, `pdf` en BD
     - Actualizar estado a `ACEPTADO` o `PENDIENTE`
   - Si `success = false`:
     - Registrar error en log
     - Marcar comprobante como `ERROR_ENVIO`
     - Permitir reintento

5. **Guardar Archivos (Opcional)**
   - Descargar XML y PDF para archivo local
   - Guardar en directorio: `/uploads/comprobantes/{año}/{mes}/`

## Validaciones Importantes

### Antes de Enviar a APISUNAT

- ✅ Cliente con RUC (6) obligatorio para FACTURAS
- ✅ Cliente con dirección obligatorio para FACTURAS
- ✅ Valor unitario SIN IGV (usar fórmula: precio / 1.18)
- ✅ Total calculado correctamente
- ✅ Serie y número no duplicados
- ✅ Fecha de emisión no mayor a fecha actual
- ✅ Cuotas suman el total (en ventas a crédito)

### Cálculos IGV

```java
// Ejemplo: Producto con precio de venta S/ 100.00 (incluye IGV)
BigDecimal precioConIgv = new BigDecimal("100.00");
BigDecimal igvFactor = new BigDecimal("1.18");
BigDecimal valorUnitario = precioConIgv.divide(igvFactor, 6, RoundingMode.HALF_UP);
// valorUnitario = 84.745763

BigDecimal baseImponible = valorUnitario.multiply(cantidad);
BigDecimal montoIgv = baseImponible.multiply(new BigDecimal("0.18"));
BigDecimal totalItem = baseImponible.add(montoIgv);
```

## Notas Adicionales

- **Sandbox**: Usar datos de prueba, no afecta SUNAT real
- **Producción**: Requiere certificado digital (.pfx) y credenciales SOL activas
- **Límites**: Consultar con APISUNAT límites de requests por mes según plan
- **Reintentos**: Si hay error de red, implementar reintentos con backoff exponencial
- **Notificaciones**: Enviar PDF por email al cliente después de emisión exitosa

## Soporte

- Email: soporte@apisunat.pe
- WhatsApp: +51 999 999 999 (verificar en web oficial)
- Documentación: https://apisunat.pe/docs
