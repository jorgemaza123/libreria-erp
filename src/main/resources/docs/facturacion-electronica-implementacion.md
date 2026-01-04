# Implementación Completa - Facturación Electrónica SUNAT

## Resumen de la Implementación

Se ha implementado un sistema de **FACTURACIÓN DUAL-MODE** que soporta:

### MODO 1 - Facturación Interna (facturaElectronicaActiva = false)
- ✅ Usa series internas: **I001** (boletas), **IF001** (facturas), **NI001** (notas)
- ✅ NO envía nada a SUNAT
- ✅ Genera PDF local como siempre
- ✅ Correlativos independientes de SUNAT

### MODO 2 - Facturación Electrónica (facturaElectronicaActiva = true)
- ✅ Usa series oficiales SUNAT: **B001** (boletas), **F001** (facturas)
- ✅ Envía AUTOMÁTICAMENTE cada comprobante a SUNAT
- ✅ Guarda hash, XML, CDR, PDF en la tabla Venta
- ✅ Sincroniza correlativos con SUNAT al activar

---

## Archivos Creados/Modificados

### 1. Entidades

#### ConfiguracionSunat.java (NUEVO)
```
src/main/java/com/libreria/sistema/model/ConfiguracionSunat.java
```
**Campos**:
- rucEmisor, razonSocialEmisor, nombreComercial
- direccionFiscal, ubigeoEmisor
- tokenApiSunat (TEXT), urlApiSunat
- facturaElectronicaActiva (Boolean) ← **TOGGLE PRINCIPAL**
- fechaCreacion, fechaActualizacion

#### Venta.java (MODIFICADO)
```
src/main/java/com/libreria/sistema/model/Venta.java
```
**Campos agregados**:
- sunatEstado (NULL, PENDIENTE, ACEPTADO, RECHAZADO)
- sunatHash
- sunatXmlUrl
- sunatCdrUrl
- sunatPdfUrl
- sunatFechaEnvio
- sunatMensajeError (TEXT)

---

### 2. DTOs

#### SunatRequestDTO.java (NUEVO)
```
src/main/java/com/libreria/sistema/model/dto/SunatRequestDTO.java
```
Mapea al JSON de REQUEST de APISUNAT. Incluye:
- Clases internas: `ItemDTO`, `CuotaDTO`
- Anotaciones `@JsonProperty` para nombres de campo correctos

#### SunatResponseDTO.java (NUEVO)
```
src/main/java/com/libreria/sistema/model/dto/SunatResponseDTO.java
```
Mapea al JSON de RESPONSE de APISUNAT. Incluye:
- Clases internas: `PayloadDTO`, `PdfDTO`
- Campos: success, message, payload.estado, payload.hash, etc.

---

### 3. Repositorios

#### ConfiguracionSunatRepository.java (NUEVO)
```
src/main/java/com/libreria/sistema/repository/ConfiguracionSunatRepository.java
```
**Métodos**:
- `findFirstByOrderByIdDesc()` - Obtiene la configuración actual
- `findByFacturaElectronicaActiva(Boolean)` - Busca por estado activo

---

### 4. Servicios

#### FacturacionElectronicaService.java (NUEVO)
```
src/main/java/com/libreria/sistema/service/FacturacionElectronicaService.java
```

**Métodos principales**:

1. **`enviarComprobanteSunat(Long ventaId)`**
   - Envía un comprobante a APISUNAT
   - Mapea Venta → SunatRequestDTO
   - Procesa respuesta y actualiza Venta
   - Maneja errores

2. **`sincronizarConSunat()`** ⭐ NUEVO
   - Consulta el último número usado en SUNAT para series B001 y F001
   - Actualiza correlativos locales
   - Se ejecuta al ACTIVAR facturación electrónica por primera vez

3. **`obtenerSerie(String tipo, boolean facturaElectronicaActiva)`** ⭐ NUEVO
   - Devuelve la serie correcta según el modo:
     - Si activa = "B001", "F001"
     - Si inactiva = "I001", "IF001"

4. **`validarConfiguracion()`**
   - Verifica que la config esté completa antes de enviar

5. **`isFacturacionElectronicaActiva()`**
   - Retorna si el modo electrónico está activo

6. **`obtenerConfiguracionActual()`**
   - Retorna la configuración SUNAT actual

---

#### VentaService.java (NUEVO) ⭐ CORE
```
src/main/java/com/libreria/sistema/service/VentaService.java
```

**Método principal**:

**`crearVenta(VentaDTO dto)`**

Flujo completo:
1. Verifica si `facturaElectronicaActiva` está activa
2. Obtiene o crea cliente
3. Determina serie según modo (I001/IF001 vs B001/F001)
4. Obtiene correlativo y genera número
5. Crea cabecera de venta
6. Procesa detalles (stock, kardex, cálculos)
7. Procesa forma de pago (contado/crédito)
8. Guarda venta
9. Registra pago y movimiento de caja
10. **SI está en modo electrónico**: Envía automáticamente a SUNAT

**Retorna**:
```json
{
  "id": 123,
  "serie": "B001",
  "numero": 45,
  "estadoSunat": "ACEPTADO",
  "facturaElectronica": true
}
```

**Métodos auxiliares**:
- `obtenerOCrearCliente()` - Gestión de clientes
- `procesarDetalles()` - Validación de stock, cálculos, kardex
- `procesarFormaPago()` - Manejo de contado/crédito
- `registrarPagoYCaja()` - Amortización y movimiento de caja
- `mapearTipoAfectacion()` - Convierte "GRAVADO" → "10"

---

### 5. Controladores

#### ConfiguracionSunatController.java (NUEVO)
```
src/main/java/com/libreria/sistema/controller/ConfiguracionSunatController.java
```

**Rutas**:
- `GET /configuracion/sunat` - Vista de configuración
- `POST /configuracion/sunat/guardar` - Guardar configuración
- `POST /configuracion/sunat/toggle` - Activar/Desactivar facturación electrónica
- `POST /configuracion/sunat/test-conexion` - Test de conexión con APISUNAT

---

### 6. Configuración Inicial

#### DataInitializer.java (MODIFICADO)
```
src/main/java/com/libreria/sistema/config/DataInitializer.java
```

**Correlativos agregados**:
```java
// Series OFICIALES (SUNAT)
BOLETA  - B001  (oficial)
FACTURA - F001  (oficial)

// Series INTERNAS (Sin SUNAT)
BOLETA     - I001   (interno)
FACTURA    - IF001  (interno)
NOTA_VENTA - NI001  (interno)
```

---

## Cómo Integrar en VentaController

### Opción 1: Refactorizar el método existente (RECOMENDADO)

En `VentaController.java`, reemplaza el método `guardarVenta()`:

```java
@Autowired
private VentaService ventaService;

@PostMapping("/api/guardar")
public ResponseEntity<?> guardarVenta(@RequestBody VentaDTO dto) {
    try {
        Map<String, Object> resultado = ventaService.crearVenta(dto);
        return ResponseEntity.ok(resultado);

    } catch (OptimisticLockingFailureException e) {
        return ResponseEntity.badRequest()
            .body("ERROR DE STOCK: Otro vendedor actualizó el producto. Intente nuevamente.");

    } catch (Exception e) {
        e.printStackTrace();
        return ResponseEntity.badRequest()
            .body("Error: " + e.getMessage());
    }
}
```

### Opción 2: Mantener ambos (para testing gradual)

Crea un nuevo endpoint:

```java
@PostMapping("/api/guardar-v2")
public ResponseEntity<?> guardarVentaV2(@RequestBody VentaDTO dto) {
    try {
        Map<String, Object> resultado = ventaService.crearVenta(dto);
        return ResponseEntity.ok(resultado);
    } catch (Exception e) {
        return ResponseEntity.badRequest().body("Error: " + e.getMessage());
    }
}
```

---

## Flujo de Uso

### Paso 1: Configurar SUNAT (Solo ADMIN)

1. Ir a `/configuracion/sunat`
2. Llenar datos:
   - RUC Emisor (11 dígitos)
   - Razón Social
   - Dirección Fiscal
   - Ubigeo (6 dígitos)
   - Token de APISUNAT
   - URL: `https://sandbox.apisunat.pe` (pruebas) o `https://app.apisunat.pe` (producción)
3. Guardar

### Paso 2: Activar Facturación Electrónica

1. En `/configuracion/sunat`, activar el toggle
2. El sistema ejecutará **automáticamente** `sincronizarConSunat()`
3. Esto consultará a SUNAT los últimos números usados y sincronizará correlativos

### Paso 3: Crear Ventas

**Modo Interno (toggle OFF)**:
- Al crear venta → usa serie I001/IF001
- NO envía a SUNAT
- PDF local normal

**Modo Electrónico (toggle ON)**:
- Al crear venta → usa serie B001/F001
- Envía AUTOMÁTICAMENTE a SUNAT
- Guarda hash, XML, CDR, PDF en la venta
- Actualiza `sunatEstado` = ACEPTADO/RECHAZADO/ERROR

---

## Validaciones Importantes

Antes de activar facturación electrónica:

✅ Configuración SUNAT completa
✅ Token válido de APISUNAT
✅ URL correcta (sandbox o producción)
✅ Clientes con RUC para facturas
✅ Clientes con dirección para facturas

---

## Estados SUNAT en Venta

| Estado | Descripción |
|--------|-------------|
| `NULL` | No enviado a SUNAT (modo interno) |
| `PENDIENTE` | Enviado, esperando respuesta |
| `ACEPTADO` | Comprobante aceptado por SUNAT |
| `RECHAZADO` | Comprobante rechazado |
| `ERROR_ENVIO` | Error al enviar (red, timeout, etc.) |

---

## Endpoints de APISUNAT Utilizados

### 1. Enviar Comprobante
```
POST /api/v3/documents
```
Body: SunatRequestDTO
Response: SunatResponseDTO

### 2. Consultar Último Número (Sincronización)
```
GET /api/v3/documents/last?serie=B001
```
Response:
```json
{
  "numero": 123
}
```

---

## Próximos Pasos

1. **Crear vista Thymeleaf** para `/configuracion/sunat`
2. **Actualizar frontend de ventas** para mostrar:
   - Indicador de modo (Interno/Electrónico)
   - Serie que se usará
   - Estado SUNAT después de guardar
3. **Agregar botón** "Reenviar a SUNAT" para comprobantes con error
4. **Implementar descarga** de XML y PDF desde URLs guardadas
5. **Agregar validación** de RUC con API externa (opcional)
6. **Configurar Bean RestTemplate** en Spring Config

---

## Dependencias Requeridas

Verificar en `pom.xml`:

```xml
<!-- Jackson para JSON (ya incluido en spring-boot-starter-web) -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>

<!-- Spring Web (ya incluido) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

---

## Testing

### 1. Test en Sandbox

1. Activar facturación electrónica
2. Configurar URL: `https://sandbox.apisunat.pe`
3. Usar token de prueba
4. Crear ventas de prueba
5. Verificar que se envían correctamente

### 2. Test de Sincronización

```bash
# Llamar endpoint de sincronización
POST /configuracion/sunat/sincronizar
```

Debe retornar:
```
Sincronización completada:
- BOLETA B001: último número = 0
- FACTURA F001: último número = 0
```

---

## Seguridad

⚠️ **IMPORTANTE**:

1. **Token APISUNAT**: Actualmente se guarda en texto plano. RECOMENDADO: Encriptar antes de guardar en BD.
2. **Acceso a configuración**: Solo ROLE_ADMIN debe tener acceso.
3. **Logs**: No registrar tokens en logs de aplicación.

---

## Soporte

Para problemas con APISUNAT:
- Documentación: https://apisunat.pe/docs
- Email: soporte@apisunat.pe

Para problemas con la implementación:
- Revisar logs en consola Spring Boot
- Verificar tabla `ventas` campo `sunat_mensaje_error`
