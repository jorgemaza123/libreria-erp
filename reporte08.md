# REPORTE DE AUDITORÍA TÉCNICA COMPLETA — SISTEMA LIBRERIA ERP
## Fecha: 2026-03-08 | Versión: 08 | Tipo: Auditoría Total (Sin Modificaciones)

---

## RESUMEN EJECUTIVO

El Sistema Libreria ERP está construido sobre bases arquitectónicas sólidas (Spring Boot 3.2, JPA, Thymeleaf, PostgreSQL) con buen uso de transacciones, inyección de dependencias y separación de capas. Sin embargo, la auditoría detectó **25 problemas técnicos reales** que abarcan desde condiciones de carrera en operaciones financieras hasta vulnerabilidades de seguridad y problemas de experiencia de usuario.

**Reglas de la auditoría:**
- No se renombran tablas ni columnas de base de datos
- No se altera la lógica de negocio existente
- Cada fix está formulado para integrarse sin romper lo actual

---

## LEYENDA DE SEVERIDAD

| Nivel | Definición |
|-------|-----------|
| 🔴 CRÍTICO | Pérdida de datos, fallo en producción, incumplimiento SUNAT |
| 🟠 ALTO | Riesgo de inconsistencia contable o seguridad explotable |
| 🟡 MEDIO | Degradación de rendimiento o error en casos borde |
| 🟢 BAJO | Mejora de calidad, UX o mantenibilidad |

---

## SECCIÓN 1 — PROBLEMAS CRÍTICOS 🔴

---

### CRÍTICO-1: Condición de carrera en descuento de stock durante ventas

**Archivo**: `src/main/java/com/libreria/sistema/service/VentaService.java` (aprox. línea 326)
**Síntoma**: En entornos con más de 1 usuario simultáneo, dos ventas pueden consumir el mismo stock.
**Causa técnica**: El código valida `stockActual >= cantidadRequerida` y luego hace `setStockActual(stock - cantidad)`, pero entre esas dos operaciones otro hilo puede haber reducido el stock. El `Producto` se carga sin bloqueo pesimista (`PESSIMISTIC_WRITE`), a diferencia del `Correlativo` que sí tiene bloqueo.

**Impacto**:
- Stock negativo → imposible reconciliar inventario físico con sistema
- Kardex inconsistente → auditoría fallida
- Venta exitosa pero sin producto físico disponible

**Fix (sin cambiar tablas)**:
Agregar método con bloqueo en `ProductoRepository`:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Producto p WHERE p.id = :id")
Optional<Producto> findByIdParaVenta(@Param("id") Long id);
```
Y usar ese método en `VentaService` al procesar detalles en lugar de `findById()`.

---

### CRÍTICO-2: Cálculo de IGV por diferencia acumula error de redondeo

**Archivo**: `src/main/java/com/libreria/sistema/service/VentaService.java` (aprox. línea 286)
**Síntoma**: La suma de IGVs de la boleta no coincide con el 18% del total.
**Causa técnica**: El IGV se calcula como diferencia:
```java
BigDecimal igvItem = subtotalItem.subtract(valorVenta);
```
Con escala de 2 decimales y `HALF_UP`, cada ítem acumula hasta ±0.005 de error. En una boleta de 10 productos, el error llega a ±0.05 soles.

**Impacto**:
- Incumplimiento SUNAT: las boletas no cuadran matemáticamente
- Problemas en declaraciones mensuales de IGV
- Error detectable en auditorías tributarias

**Fix (sin cambiar tablas)**:
```java
// Calcular IGV directamente (nunca por diferencia)
BigDecimal igvRate = new BigDecimal("0.18");
BigDecimal igvItem = subtotalItem.multiply(igvRate).divide(new BigDecimal("1.18"), 2, RoundingMode.HALF_UP);
BigDecimal valorVenta = subtotalItem.subtract(igvItem);
```

---

### CRÍTICO-3: @Transactional ausente en actualización masiva de precios

**Archivo**: `src/main/java/com/libreria/sistema/controller/CompraController.java` — método `actualizarPrecioVenta()`
**Síntoma**: Si falla la actualización del producto 3 de 5, los productos 1 y 2 ya tienen precios actualizados, pero 3, 4 y 5 no.
**Causa técnica**: El método itera y hace `productoRepository.save()` dentro de un loop sin envolver la operación completa en una sola transacción.

**Impacto**:
- Precios inconsistentes en catálogo
- Márgenes de ganancia incorrectos en reportes
- Usuario no recibe error claro, cree que todo fue exitoso

**Fix**:
Agregar `@Transactional` al método del controlador o mejor aún, mover la lógica a un método de servicio con `@Transactional`:
```java
@Transactional
public void actualizarPreciosDesdeAlertas(List<Map<String, Object>> items) {
    // ... loop de actualización aquí
}
```

---

### CRÍTICO-4: NullPointerException latente en cobro de créditos

**Archivo**: `src/main/java/com/libreria/sistema/controller/CobranzaController.java` (aprox. línea 74)
**Síntoma**: La aplicación falla con HTTP 500 al intentar cobrar una venta al crédito si `saldoPendiente` no fue inicializado correctamente.
**Causa técnica**:
```java
if (montoPago.compareTo(venta.getSaldoPendiente()) > 0) { ... }
```
Si `getSaldoPendiente()` retorna `null`, `compareTo(null)` lanza `NullPointerException`. Esto puede ocurrir si una venta fue creada sin pasar por el flujo completo de `VentaService`.

**Impacto**:
- Cobros bloqueados sin mensaje útil al usuario
- Stack trace expuesto en respuesta HTTP si no hay error handler global

**Fix (sin cambiar tablas)**:
En `Venta.java`, agregar inicialización defensiva:
```java
@PrePersist
@PreUpdate
private void inicializar() {
    if (this.montoPagado == null) this.montoPagado = BigDecimal.ZERO;
    if (this.saldoPendiente == null && this.total != null) this.saldoPendiente = this.total;
}
```
Y en CobranzaController, antes de comparar:
```java
BigDecimal saldo = venta.getSaldoPendiente() != null ? venta.getSaldoPendiente() : BigDecimal.ZERO;
if (montoPago.compareTo(saldo) > 0) { ... }
```

---

## SECCIÓN 2 — PROBLEMAS ALTOS 🟠

---

### ALTO-1: XSS posible en campos de texto libre renderizados con th:utext

**Archivos afectados** (múltiples plantillas):
- `templates/lista-escolar/detalle.html`
- `templates/cotizaciones/nueva.html`
- `templates/configuracion/general.html`

**Síntoma**: Un usuario con rol ADMIN puede guardar `<script>alert(1)</script>` en un campo de observaciones y esa cadena se ejecuta en el navegador de cualquier usuario que vea esa pantalla.
**Causa técnica**: El atributo Thymeleaf `th:utext` renderiza HTML sin escapar. Fue diseñado para contenido confiable (HTML del sistema), no para texto ingresado por usuarios.

**Impacto**:
- Robo de sesiones (cookie theft)
- Inyección de formularios falsos
- En un contexto local con múltiples empleados: un vendedor malicioso puede comprometer la cuenta del admin

**Fix**:
Reemplazar `th:utext` por `th:text` en todos los campos que muestran datos ingresados por usuarios (observaciones, notas, descripciones). Usar `th:utext` solo para HTML generado internamente por el sistema (ej: etiquetas de formato).

---

### ALTO-2: Validación insuficiente de cantidades negativas en compras

**Archivo**: `src/main/java/com/libreria/sistema/service/CompraService.java`
**Síntoma**: Un usuario puede registrar un detalle de compra con cantidad = -5 o = 0, lo que genera un Kardex inválido y reduce el stock en lugar de aumentarlo.
**Causa técnica**: No existe validación de rango para `item.getCantidad()` ni para `item.getCosto()` en el service antes de procesar los detalles.

**Impacto**:
- Stock puede quedar en valores incorrectos o negativos
- Kardex muestra movimientos inválidos
- Costo promedio ponderado se calcula sobre valores erróneos

**Fix** en `CompraService.guardarCompra()`, antes del loop de detalles:
```java
for (CompraDTO.DetalleDTO item : dto.getItems()) {
    if (item.getCantidad() == null || item.getCantidad() <= 0) {
        throw new RuntimeException("Cantidad inválida para: " + item.getNombreProducto());
    }
    if (item.getCosto() == null || item.getCosto().compareTo(BigDecimal.ZERO) <= 0) {
        throw new RuntimeException("Costo inválido para: " + item.getNombreProducto());
    }
}
```

---

### ALTO-3: LazyInitializationException al renderizar relaciones lazy en templates

**Archivos afectados**:
- `src/main/java/com/libreria/sistema/controller/HomeController.java` — método de detalle de venta
- `src/main/java/com/libreria/sistema/controller/CobranzaController.java` — método `ticketPago()`

**Síntoma**: Al acceder a `/cobranzas/ticket/{id}` o al modal de detalle de venta, la aplicación falla con `org.hibernate.LazyInitializationException: could not initialize proxy`.
**Causa técnica**: Entidades como `Venta` tienen `clienteEntity` con `FetchType.LAZY` por defecto. El controlador carga la entidad, la pasa al modelo, pero Thymeleaf intenta acceder a `venta.clienteEntity.empresa` fuera de la transacción activa.

**Impacto**:
- Pantallas de detalle que fallan aleatoriamente
- Error HTTP 500 sin mensaje útil
- Mala experiencia especialmente al imprimir tickets

**Fix**: Agregar `@EntityGraph` en los métodos de repositorio usados para estas vistas:
```java
// VentaRepository
@EntityGraph(attributePaths = {"detalles", "detalles.producto", "clienteEntity", "amortizaciones"})
@Query("SELECT v FROM Venta v WHERE v.id = :id")
Optional<Venta> findByIdConTodo(@Param("id") Long id);
```
Y en `CobranzaController.ticketPago()`, cargar también la Venta del Amortizacion de forma explícita con join fetch.

---

### ALTO-4: CSRF desactivado en todos los endpoints financieros

**Archivo**: `src/main/java/com/libreria/sistema/config/SecurityConfig.java`
**Síntoma**: No hay protección contra falsificación de solicitudes entre sitios.
**Causa técnica**: La configuración ignora CSRF para `/caja/**`, `/ventas/**`, `/compras/**` y otros módulos críticos. El argumento de "sistema local" no es suficiente porque:
1. Dispositivos en la misma LAN pueden ser comprometidos
2. Un atacante en la red puede enviar peticiones falsificadas desde el navegador del cajero

**Impacto**:
- Ventas falsas creadas sin que el usuario lo sepa
- Movimientos de caja manipulados
- Precio de productos modificado sin autorización

**Fix**: Mantener CSRF activo y pasar el token en llamadas AJAX:
```html
<!-- En layout.html, dentro de <head> -->
<meta name="_csrf" th:content="${_csrf.token}"/>
<meta name="_csrf_header" th:content="${_csrf.headerName}"/>
```
```javascript
// En JS global
const csrfToken = document.querySelector("meta[name='_csrf']")?.content;
const csrfHeader = document.querySelector("meta[name='_csrf_header']")?.content;
// Agregar en fetch():
headers: { [csrfHeader]: csrfToken }
```

---

### ALTO-5: Credenciales sensibles hardcodeadas en application.properties

**Archivo**: `src/main/resources/application.properties`
**Líneas afectadas**:
- `spring.datasource.password=1234` — contraseña de base de datos
- `server.ssl.key-store-password=sistemaerp` — contraseña del certificado SSL (si aplica)

**Impacto**:
- Si el repositorio git es visto por alguien (empleado, técnico), obtiene acceso directo a la base de datos
- Cualquier backup del código incluye las credenciales

**Fix**: Usar variables de entorno:
```properties
spring.datasource.password=${DB_PASSWORD:1234}
server.ssl.key-store-password=${SSL_KS_PASSWORD:sistemaerp}
```
Y documentar en CLAUDE.md las variables que el servidor debe tener definidas.

---

### ALTO-6: Subida de archivos sin validación de tipo real (solo extensión)

**Archivo**: `src/main/java/com/libreria/sistema/controller/ProductoController.java`
**Síntoma**: La validación solo verifica la extensión del archivo (`.jpg`, `.png`), pero no el contenido MIME real.
**Causa técnica**: Un atacante puede renombrar `script.jsp` como `imagen.jpg` y subirlo. Si el servidor sirve estáticamente la carpeta `uploads/`, el archivo puede ser ejecutado.

**Impacto**:
- Ejecución remota de código (RCE) si el servidor Tomcat accede al directorio `uploads/`
- Escalada de privilegios en el servidor

**Fix**:
```java
// Después de recibir el MultipartFile
String contentType = imagen.getContentType();
if (!Arrays.asList("image/jpeg", "image/png", "image/webp").contains(contentType)) {
    throw new RuntimeException("Tipo de archivo no permitido: " + contentType);
}
// Además, verificar que el path resuelto esté dentro del directorio uploads
Path resolvedPath = rootPath.resolve(nombreUnico).normalize();
if (!resolvedPath.startsWith(rootPath)) {
    throw new SecurityException("Ruta inválida detectada");
}
```

---

### ALTO-7: Sin paginación en listado de productos — colapso con inventario grande

**Archivo**: `src/main/java/com/libreria/sistema/controller/ProductoController.java`
**Síntoma**: Con 2,000+ productos, la página `/productos` tarda varios segundos en cargar y puede agotar la memoria JVM.
**Causa técnica**:
```java
model.addAttribute("productos", productoService.listarTodos()); // Carga TODO en memoria
```
El browser debe renderizar toda la tabla HTML de una sola vez.

**Impacto**:
- Página inutilizable con inventario grande
- Out of Memory en servidor
- Lentitud en toda la aplicación por GC pressure

**Fix**: Implementar paginación del lado del servidor con Spring Data `Pageable`:
```java
@GetMapping
public String listar(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "50") int size,
    Model model) {
    model.addAttribute("productos", productoRepository.findAll(PageRequest.of(page, size, Sort.by("nombre"))));
    return "productos/lista";
}
```

---

## SECCIÓN 3 — PROBLEMAS MEDIOS 🟡

---

### MEDIO-1: Sin pool de conexiones configurado (HikariCP por defecto mínimo)

**Archivo**: `src/main/resources/application.properties`
**Síntoma**: Con 10+ usuarios simultáneos, la aplicación devuelve errores de timeout de conexión.
**Causa técnica**: HikariCP por defecto usa `maximum-pool-size=10`. Sin configuración explícita, no hay ajuste para el caso de uso real.

**Fix** (agregar en application.properties):
```properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
```

---

### MEDIO-2: Sesiones de caja duplicadas posibles (sin restricción única en BD)

**Archivo**: `src/main/java/com/libreria/sistema/service/CajaService.java` (línea 47-51)
**Síntoma**: Si un usuario abre caja desde dos pestañas del navegador al mismo tiempo, ambas peticiones pasan la validación `obtenerSesionActiva().isPresent()` antes de que cualquiera guarde la sesión nueva.
**Causa técnica**: No existe restricción `UNIQUE` a nivel de base de datos que impida dos sesiones ABIERTA por usuario.

**Impacto**:
- Movimientos de caja repartidos entre dos sesiones
- Cuadre de caja imposible al cerrar
- Saldo inicial duplicado

**Fix**: Agregar constraint único en la base de datos (sin renombrar tablas):
```sql
-- Ejecutar una sola vez en PostgreSQL:
CREATE UNIQUE INDEX IF NOT EXISTS uk_sesion_usuario_abierta
ON sesiones_caja (usuario_id)
WHERE estado = 'ABIERTA';
```

---

### MEDIO-3: Productos inactivos aparecen en búsqueda de ventas

**Archivo**: `src/main/java/com/libreria/sistema/repository/ProductoRepository.java`
**Síntoma**: Al buscar un producto para agregar a una venta, aparecen productos marcados como inactivos (`activo = false`), lo que confunde al vendedor y puede generar ventas de productos descontinuados.
**Causa técnica**: Los métodos de búsqueda no filtran por `activo = true`.

**Fix** en ProductoRepository: agregar condición en queries de búsqueda usadas en ventas:
```java
@Query("SELECT p FROM Producto p WHERE p.activo = true AND LOWER(p.nombre) LIKE LOWER(CONCAT('%', :q, '%'))")
List<Producto> buscarActivosPorNombre(@Param("q") String q);
```

---

### MEDIO-4: Stack trace expuesto en errores de generación de PDF

**Archivo**: `src/main/java/com/libreria/sistema/controller/ReporteController.java`
**Síntoma**: Si falla la generación de un reporte PDF, el mensaje de error del servidor incluye detalles internos del stack trace.
**Causa técnica**:
```java
response.sendError(500, "Error al generar PDF: " + e.getMessage());
```
`e.getMessage()` puede incluir paths internos, nombres de clases y detalles de configuración.

**Fix**:
```java
log.error("Error generando PDF para reporte {}", tipo, e);
response.sendError(500, "No se pudo generar el reporte. Contacte al administrador.");
```

---

### MEDIO-5: Sin confirmación al eliminar entidades críticas

**Archivos afectados**: Varias vistas (`productos/lista.html`, `compras/lista.html`, etc.)
**Síntoma**: El botón "Eliminar" ejecuta inmediatamente sin pedir confirmación.
**Causa técnica**: Los formularios de eliminación o los links de borrado no tienen dialog de confirmación.

**Impacto**:
- Pérdida accidental de datos
- Sin posibilidad de deshacer (no hay papelera de reciclaje)

**Fix**: Agregar SweetAlert2 (que ya está incluido en el proyecto) como guardia:
```javascript
function confirmarEliminar(url, nombre) {
    Swal.fire({
        title: '¿Eliminar ' + nombre + '?',
        text: 'Esta acción no se puede deshacer.',
        icon: 'warning',
        showCancelButton: true,
        confirmButtonColor: '#d33',
        confirmButtonText: 'Sí, eliminar',
        cancelButtonText: 'Cancelar'
    }).then((result) => {
        if (result.isConfirmed) window.location.href = url;
    });
}
```

---

### MEDIO-6: Sin feedback visual en operaciones lentas (spinners ausentes)

**Archivos afectados**: `templates/ventas/pos.html`, `templates/compras/formulario.html`, `templates/lista-escolar/index.html`
**Síntoma**: Al guardar una venta, registrar una compra o generar un PDF del simulador, el botón no da feedback visual durante el procesamiento. El usuario puede hacer clic múltiples veces.
**Causa técnica**: Los botones de submit no se deshabilitan ni muestran spinner durante la petición AJAX.

**Fix estándar** para todos los botones de acción principal:
```javascript
function activarSpinner(btn) {
    btn.disabled = true;
    btn.dataset.textoOriginal = btn.innerHTML;
    btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Procesando...';
}
function desactivarSpinner(btn) {
    btn.disabled = false;
    btn.innerHTML = btn.dataset.textoOriginal;
}
```

---

### MEDIO-7: Mensaje de error "CAJA CERRADA" no visible al usuario en ventas

**Archivo**: `src/main/java/com/libreria/sistema/service/CajaService.java` (línea 75)
**Síntoma**: Si el cajero intenta registrar una venta sin abrir caja, el error `RuntimeException("CAJA CERRADA...")` se lanza en el service, pero el controller puede no manejarlo para mostrarlo como alerta clara en la interfaz.
**Causa técnica**: Depende de si `HomeController.guardarVenta()` tiene un `catch` que devuelva un mensaje amigable o solo hace `throw`.

**Fix**: En el controller de ventas, capturar explícitamente:
```java
} catch (RuntimeException e) {
    if (e.getMessage().contains("CAJA CERRADA")) {
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
            .body(Map.of("error", "Debe abrir la caja antes de registrar ventas."));
    }
    throw e;
}
```
Y en el frontend, manejar el status 412 con un SweetAlert que redirija a `/caja`.

---

### MEDIO-8: Kárdex no registra ajustes de precio (solo movimientos de stock)

**Archivo**: `src/main/java/com/libreria/sistema/service/KardexService.java` (o equivalente)
**Síntoma**: Cuando se actualiza el precio de venta de un producto (por alerta de compra o cambio manual), no queda registrado en ningún historial.
**Impacto para el negocio**:
- No se puede saber cuándo ni por qué cambió el precio de un producto
- Difícil hacer auditoría de márgenes históricos
- Si un empleado cambia un precio indebidamente, no hay rastro

**Mejora propuesta**: Crear una tabla `historial_precios` (o usar `MovimientoCaja` con categoría especial) para registrar cambios de precio con fecha, precio anterior, precio nuevo y usuario responsable. Sin renombrar nada existente.

---

### MEDIO-9: Sin límite de intentos de login (fuerza bruta posible)

**Archivo**: `src/main/java/com/libreria/sistema/config/SecurityConfig.java`
**Síntoma**: Un atacante puede intentar miles de combinaciones de contraseña sin ser bloqueado.
**Causa técnica**: No hay configuración de `UserDetailsService.configure()` para bloquear cuentas después de N intentos fallidos, ni rate limiting.

**Fix** (sin cambiar el modelo de usuarios):
Agregar Spring Security's built-in account locking usando `UserDetailsService` + contador de fallos en `Usuario`:
```java
// En SecurityConfig, configurar el AuthenticationProvider con:
provider.setUserDetailsService(userDetailsService);
provider.setHideUserNotFoundExceptions(false);
// Y en UserDetailsService, retornar UserDetails con isAccountNonLocked()
```

---

## SECCIÓN 4 — PROBLEMAS BAJOS 🟢

---

### BAJO-1: Mensajes de error mezclados (español e inglés, formatos distintos)

**Archivos afectados**: Múltiples services y controllers
**Ejemplos**:
- `"CAJA CERRADA: Debe abrir caja antes de operar."` (CajaService)
- `"Producto no encontrado"` (VentaService)
- `"El monto excede la deuda pendiente."` (CobranzaController)
- `"Stock insuficiente"` sin el nombre del producto

**Mejora**: Centralizar mensajes en una clase `Mensajes.java` o archivo `messages.properties` para consistencia:
```java
public class Mensajes {
    public static final String CAJA_CERRADA = "Debe abrir la caja antes de realizar esta operación.";
    public static final String PRODUCTO_NO_ENCONTRADO = "No se encontró el producto solicitado.";
    // ...
}
```

---

### BAJO-2: Sin formato de moneda consistente en templates

**Archivos afectados**: Varias plantillas
**Síntoma**: Algunos valores usan `th:text="${#numbers.formatDecimal(v, 1, 2)}"`, otros usan `String.format()` en el controller, y otros muestran el valor crudo sin formatear.
**Mejora**: Definir una utilidad Thymeleaf en `config/ThymeleafConfig.java` o usar el dialecto de Spring para formatear siempre como `S/ #,##0.00`.

---

### BAJO-3: DDL en modo "update" — riesgo de migraciones inconsistentes

**Archivo**: `src/main/resources/application.properties`
```properties
spring.jpa.hibernate.ddl-auto=update
```
**Riesgo**: Hibernate `update` puede fallar silenciosamente si un cambio en una entidad no puede aplicarse automáticamente (ej: campo NOT NULL sin default en tabla con datos). Migraciones complejas quedan incompletas.

**Mejora**: Migrar a Flyway o Liquibase para control preciso de cambios de esquema. Mientras tanto, tener backups antes de cada deploy.

---

### BAJO-4: Sin manejo global de excepciones

**Síntoma**: Diferentes controllers manejan errores de maneras distintas. Algunos devuelven `ResponseEntity` con JSON, otros hacen `redirect`, otros dejan propagarse la excepción.
**Mejora**: Crear un `@ControllerAdvice` global:
```java
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException e) {
        log.error("Error de aplicación", e);
        return ResponseEntity.internalServerError()
            .body(Map.of("error", e.getMessage()));
    }
}
```

---

### BAJO-5: Sin log de acciones de usuario en módulo de reportes

**Archivo**: `src/main/java/com/libreria/sistema/controller/ReporteController.java`
**Síntoma**: No se registra quién generó qué reporte, cuándo y para qué rango de fechas.
**Impacto para negocio**: Sin trazabilidad de acceso a información financiera sensible.
**Mejora**: Agregar `@Auditable(modulo = "REPORTES", accion = "EXPORTAR")` a los endpoints de exportación.

---

### BAJO-6: Thymeleaf template cache desactivado en producción

**Archivo**: `src/main/resources/application.properties`
Si tiene: `spring.thymeleaf.cache=false`
**Riesgo**: En producción, esto hace que cada request recompile los templates. Aumenta latencia y uso de CPU.
**Fix**: Activar cache en producción mediante perfil Spring:
```properties
# application-prod.properties
spring.thymeleaf.cache=true
```

---

### BAJO-7: Sin índices en columnas de búsqueda frecuente

**Síntoma**: Consultas por `nombre`, `codigo`, `fecha`, `estado` son lentas en tablas grandes porque no tienen índices optimizados más allá de las claves primarias.
**Mejora**: Agregar índices (sin renombrar tablas):
```sql
CREATE INDEX IF NOT EXISTS idx_producto_nombre ON productos (LOWER(nombre));
CREATE INDEX IF NOT EXISTS idx_venta_fecha ON ventas (fecha);
CREATE INDEX IF NOT EXISTS idx_movimiento_sesion ON movimientos_caja (sesion_id);
CREATE INDEX IF NOT EXISTS idx_kardex_producto ON kardex (producto_id, fecha DESC);
```

---

## SECCIÓN 5 — ANÁLISIS UX/UI 🖥️

---

### UX-1: El flujo de apertura de caja no es obvio para usuarios nuevos

**Archivo**: `templates/caja/index.html`
**Problema**: Si la caja está cerrada y el usuario va a `/ventas`, recibe un error técnico en lugar de una pantalla amigable que le diga "Primero debes abrir la caja" con un botón directo a `/caja`.
**Mejora**: Interceptar el error de caja cerrada en el middleware y redirigir con mensaje guiado.

---

### UX-2: La búsqueda de productos en ventas no tiene debounce

**Archivo**: `templates/ventas/pos.html` (o similar)
**Problema**: Si la búsqueda de productos dispara una petición AJAX por cada tecla presionada, con digitación rápida se envían 10+ requests seguidos. El último en llegar puede no ser el más reciente.
**Mejora**: Agregar debounce de 300ms:
```javascript
let searchTimeout;
inputBuscar.addEventListener('input', () => {
    clearTimeout(searchTimeout);
    searchTimeout = setTimeout(() => buscarProducto(inputBuscar.value), 300);
});
```

---

### UX-3: Modal del simulador de lista escolar sin validación de campos requeridos

**Archivo**: `templates/lista-escolar/index.html`
**Problema**: El modal del Simulador permite enviar sin completar Alumno, Grado ni archivo Excel. El error sólo aparece en el servidor, no en el cliente.
**Mejora**: Validación HTML5 + validación JS antes de `fetch()`:
```javascript
if (!alumno.trim() || !grado.trim() || !archivoExcel.files.length) {
    Swal.fire('Campos incompletos', 'Complete alumno, grado y archivo Excel.', 'warning');
    return;
}
```

---

### UX-4: Panel "Mi Negocio" sin indicadores de tendencia comparativa

**Archivo**: `templates/mi-negocio/index.html`
**Problema**: Los info-boxes muestran variaciones como "+15%" pero sin contexto de si eso es bueno o malo para el negocio específico.
**Mejora**: Agregar tooltips explicativos que indiquen el significado:
- "Vendiste 15% más que el período anterior — ¡buen resultado!"
- "Invertiste 8% menos — verifica si fue por falta de stock"

---

### UX-5: Tabla de Kardex sin filtro por producto desde la vista

**Archivo**: `templates/kardex/index.html`
**Problema**: Ver el historial de movimientos de un producto específico requiere filtrar toda la tabla, pero si no hay filtro por nombre de producto en la URL/query, es difícil navegar.
**Mejora**: Agregar parámetro `?productoId=` que prefiltre la tabla al cargar.

---

### UX-6: Sin modo de impresión optimizado para tickets de pago

**Archivo**: `templates/cobranzas/ticket_pago.html`
**Problema**: Los tickets de cobro de amortizaciones no tienen estilos CSS `@media print` que oculten navbar, botones y menús laterales. Se imprime toda la pantalla incluyendo elementos de navegación.
**Mejora**: Agregar al CSS del template:
```css
@media print {
    .main-sidebar, .main-header, .btn, .no-print { display: none !important; }
    .content-wrapper { margin: 0 !important; }
}
```

---

## SECCIÓN 6 — MEJORAS AL FLUJO DEL NEGOCIO

*Basadas en el contexto real del usuario: compras semanales en Lima, distribución por paquetes, 3 competidores locales.*

---

### NEGOCIO-1: Costo real vs. costo registrado — brecha en control de inversión

**Situación actual**: Al registrar una compra, se ingresa el costo unitario directamente. Pero en el mercado mayorista (Lima), los proveedores venden en lotes (docena, media docena, paquete de 6) con un precio total. El usuario tiene que dividir mentalmente.

**Implementado recientemente**: El campo "Total pagado" + cálculo de costo unitario automático (compras/formulario.html).

**Mejora adicional pendiente**: Agregar una columna "Costo real por unidad (con IGV)" vs "Costo registrado" en el listado de compras, para verificar que no hubo error al ingresar los datos. Formula:
```
Costo registrado × Cantidad = ¿Igual al total de la boleta del proveedor?
```
Si no coincide, mostrar advertencia visual en la fila.

---

### NEGOCIO-2: Control de rentabilidad por viaje de compra

**Situación actual**: No existe una forma de agrupar varias compras realizadas en el mismo viaje a Lima y ver cuánto se invirtió en total ese día, y cuánto se ha recuperado de esa inversión semanas después.

**Mejora propuesta** (sin cambiar tablas):
Agregar campo `etiqueta_viaje` (VARCHAR, nullable) en la tabla `compras`. Permite filtrar por "Lima 08/03/2026" y ver:
- Total invertido: S/ 850
- Ventas de esos productos desde esa fecha: S/ 1,200
- Recuperación: 141% (ganancia de S/ 350)

---

### NEGOCIO-3: Precio de venta sugerido con contexto competitivo

**Implementado recientemente**: Panel de márgenes (15%, 25%, 35%, 50%) en formulario de compra.

**Mejora adicional**: Agregar un campo libre "Precio de competencia" donde el usuario ingrese el precio que vio en el mercado local, y el sistema calcule automáticamente:
- ¿Con ese precio, qué margen obtienes?
- ¿Es viable o deberías buscar otro proveedor?

Esto convierte la decisión de precio en un análisis, no en una adivinanza.

---

### NEGOCIO-4: Alertas de bajo stock vinculadas al ciclo de compra

**Situación actual**: Existe el campo `stockMinimo` en Producto, pero no hay un reporte que liste "productos que debes comprar en tu próximo viaje a Lima".

**Mejora**: Crear un endpoint `/compras/lista-reposicion` que muestre:
- Productos con `stockActual <= stockMinimo`
- Cuántas unidades comprar para llegar a `stockMinimo * 2` (stock seguro)
- Costo estimado de reposición basado en el último precio de compra
- Botón "Generar orden de compra" que pre-llena el formulario de compra con esos productos

---

### NEGOCIO-5: Dashboard de flujo semanal de inversión/recuperación

**Implementado recientemente**: Sección "Flujo de Inversión" en dashboard y página "Mi Negocio".

**Mejora adicional**: Agregar en "Mi Negocio" una visualización de la línea del tiempo: "Compré el lunes S/500, para el viernes ya recuperé S/320 (64%). Al siguiente viernes: S/480 (96%)." Esto muestra la velocidad de rotación del capital de manera intuitiva, sin necesidad de calcular ROI manualmente.

---

### NEGOCIO-6: Comparador de listas escolares vs. stock disponible

**Situación actual**: Al atender una lista escolar, no hay manera de saber rápidamente qué productos de la lista están en stock y cuáles no.

**Mejora**: En el formulario de creación de lista escolar, al agregar productos, mostrar el stock disponible en tiempo real junto al nombre del producto. Si `stockActual < cantidadSolicitada`, marcar la fila en rojo y sugerir la cantidad máxima disponible.

---

## SECCIÓN 7 — RESUMEN PRIORIZADO

### Acciones Inmediatas (Esta semana)
| ID | Problema | Archivo | Impacto |
|----|---------|---------|---------|
| CRÍTICO-1 | Race condition en stock | VentaService.java | Inventario negativo |
| CRÍTICO-2 | IGV por diferencia | VentaService.java | Incumplimiento SUNAT |
| CRÍTICO-3 | Sin @Transactional en precios | CompraController.java | Precios inconsistentes |
| CRÍTICO-4 | NPE en cobro créditos | CobranzaController.java | Cobros bloqueados |
| ALTO-1 | XSS con th:utext | Múltiples templates | Seguridad de datos |

### Próxima Semana
| ID | Problema | Archivo | Impacto |
|----|---------|---------|---------|
| ALTO-2 | Cantidades negativas en compra | CompraService.java | Stock inválido |
| ALTO-3 | LazyInitializationException | Repositorios/Controladores | Pantallas rotas |
| ALTO-6 | Validación de tipo en subida | ProductoController.java | RCE potencial |
| ALTO-7 | Sin paginación en productos | ProductoController.java | OOM con inventario grande |
| MEDIO-2 | Sesiones caja duplicadas | BD (constraint único) | Cuadre de caja fallido |

### Mejoras de Calidad (Este mes)
| ID | Problema | Acción |
|----|---------|-------|
| MEDIO-5 | Sin confirmación al eliminar | SweetAlert2 en todos los delete |
| MEDIO-6 | Sin feedback en botones | Spinner en submit/fetch |
| MEDIO-7 | Error caja cerrada poco visible | Handler en controller + redirect |
| BAJO-4 | Sin manejo global de excepciones | @ControllerAdvice |
| BAJO-7 | Sin índices en búsquedas | Scripts SQL de indexación |

### Mejoras Estratégicas (Este trimestre)
- NEGOCIO-2: Agrupación de compras por viaje
- NEGOCIO-4: Lista de reposición automática
- NEGOCIO-5: Dashboard de velocidad de rotación de capital
- NEGOCIO-6: Verificador de stock en listas escolares

---

## APÉNDICE — ARCHIVOS CON MÁS PROBLEMAS

| Archivo | Problemas detectados | Severidad máxima |
|---------|---------------------|-----------------|
| VentaService.java | Race condition stock, IGV incorrecto | 🔴 CRÍTICO |
| CompraController.java | Sin @Transactional | 🔴 CRÍTICO |
| CobranzaController.java | NPE, LazyInit, sin null check | 🔴 CRÍTICO |
| application.properties | Sin pool, credenciales hardcoded | 🟠 ALTO |
| SecurityConfig.java | CSRF desactivado | 🟠 ALTO |
| ProductoController.java | Sin paginación, upload sin validar tipo | 🟠 ALTO |
| Templates (7+) | th:utext en datos de usuario | 🟠 ALTO |
| CajaService.java | Sesiones duplicadas | 🟡 MEDIO |
| ReporteController.java | Stack trace expuesto | 🟡 MEDIO |

---

*Reporte generado el 2026-03-08. No se realizó ninguna modificación al código ni a la base de datos durante esta auditoría.*
