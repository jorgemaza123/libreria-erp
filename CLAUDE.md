# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Sistema Libreria is a comprehensive ERP desktop application for managing a bookstore/stationery business. Built with Spring Boot 3.2.0 and Java 17, it provides a web-based interface for inventory management, sales, purchases, cash register operations, and reporting.

**Database**: PostgreSQL (`libreria_db` on localhost:5432, default credentials in application.properties)

**Access**: http://localhost:8080 (Session timeout: 12 hours)

**Default Users**:
- Admin: `admin` / `admin` (ROLE_ADMIN)
- Vendedor: `vendedor` / `1234` (ROLE_VENDEDOR)

## Build & Development Commands

**Run Application**:
```bash
./mvnw spring-boot:run
# Windows:
mvnw.cmd spring-boot:run
```

**Build Project**:
```bash
./mvnw clean package
# Windows:
mvnw.cmd clean package
```

**Clean Build Artifacts**:
```bash
./mvnw clean
```

**Run with DevTools** (auto-reload enabled by default in development):
Development mode is active when running via `spring-boot:run`.

## Architecture

### Layer Structure

```
com.libreria.sistema/
├── config/          - Spring configuration, security, data initialization
├── controller/      - MVC controllers (web endpoints)
├── model/           - JPA entities and DTOs
│   ├── dto/         - Data transfer objects
│   └── sunat/       - Peruvian tax system DTOs
├── repository/      - Spring Data JPA repositories
└── service/         - Business logic layer
```

### Security Architecture

- **Framework**: Spring Security 6 with form-based authentication
- **Password Encoding**: BCryptPasswordEncoder
- **CSRF**: Disabled to support AJAX POST requests
- **Public Resources**: `/css/**`, `/js/**`, `/img/**`, `/plugins/**`
- **Authentication**: All other routes require login via `/login`

**Important**: DataInitializer.java (config/DataInitializer.java:20) automatically creates default users, roles, correlativos, and service products on startup.

### Domain Model Overview

**Core Entities**:
- **Producto**: Inventory items with pricing, stock tracking, and barcode support
- **Venta**: Sales with SUNAT-compliant invoicing (Boleta/Factura), payment tracking (Contado/Crédito)
- **Compra**: Purchase orders with supplier tracking
- **Cliente**: Customer management with document types (DNI, RUC)
- **Proveedor**: Supplier management
- **SesionCaja**: Cash register sessions with opening/closing balance reconciliation
- **MovimientoCaja**: Cash movements (ingresos/egresos) linked to sessions
- **Kardex**: Inventory movement history (INGRESO/SALIDA/AJUSTE)
- **Amortizacion**: Credit payment installments linked to sales

**Business Logic Patterns**:
- **Correlativo**: Auto-incrementing document numbers (BOLETA, FACTURA, COTIZACION) with series (B001, F001, C001)
- **Credit Sales**: Venta tracks `montoPagado`, `saldoPendiente`, and `amortizaciones` for installment payments
- **Cash Flow**: CajaService enforces opening cash register before any transactions
- **Inventory Control**: Kardex records all stock movements with automatic stock updates

### Controllers & Routes

Major controller groups:
- **CajaController**: Cash register operations (`/caja`)
- **ProductoController**: Product CRUD, import/export (`/productos`)
- **VentaController** (via HomeController): Sales/invoicing (`/ventas`)
- **CompraController**: Purchase orders (`/compras`)
- **CobranzaController**: Credit payment collection (`/cobranzas`)
- **ReporteController**: PDF/Excel reports (`/reportes`)
- **InventarioController**: Stock adjustments (`/inventario`)
- **KardexController**: Movement history (`/kardex`)

### Service Layer Patterns

Services follow transactional patterns with `@Transactional` for data consistency:

- **CajaService** (service/CajaService.java:20): Session-based cash management with automatic balance calculation
- **ProductoService**: Import/export via Apache POI (Excel)
- **ReporteService**: PDF generation via OpenPDF, Excel via Apache POI
- **DashboardService**: Aggregated statistics and recent activity

### View Layer

**Template Engine**: Thymeleaf with Spring Security integration
- **Layout**: `templates/fragments/` contains reusable components (navbar, footer, modals)
- **Module Templates**: Organized by feature in subdirectories (`caja/`, `productos/`, `ventas/`, etc.)
- **Static Assets**: `/src/main/resources/static/` for CSS, JS, and plugins

### File Upload Configuration

- **Max File Size**: 10MB
- **Upload Directory**: `uploads/` (at project root, not in src/)
- **Supported Operations**: Product image uploads, Excel imports

## Peruvian Tax (SUNAT) Compliance

The system implements Peru's tax requirements:
- **Document Types**: BOLETA, FACTURA, NOTA_VENTA with legal series/correlatives
- **IGV (Tax)**: Tracked separately (`totalGravada`, `totalIgv`)
- **Customer Documents**: DNI (8 digits), RUC (11 digits)
- **Afectación IGV**: Products classified as GRAVADO/EXONERADO/INAFECTO

## Database Configuration

Edit `src/main/resources/application.properties` to configure:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/libreria_db
spring.datasource.username=postgres
spring.datasource.password=1234
```

**DDL Mode**: `spring.jpa.hibernate.ddl-auto=update` (auto-creates/updates tables)

**Note**: PostgreSQL must be running before starting the application.

## Key Implementation Notes

1. **Transactional Integrity**: Always use `@Transactional` in services when modifying multiple entities (e.g., creating a sale updates Venta, DetalleVenta, Kardex, and SesionCaja)

2. **Current User Context**: Services retrieve authenticated user via `SecurityContextHolder.getContext().getAuthentication().getName()`

3. **Lombok Usage**: Entities use `@Data` for getters/setters, reducing boilerplate

4. **Lazy Loading**: Most collections use `FetchType.LAZY` - be aware of LazyInitializationException outside transaction boundaries

5. **Audit Fields**: Most entities track creation timestamps and user references

6. **Business Rules**:
   - Cannot create sales/purchases without open cash session (CajaService validation)
   - Stock adjustments automatically generate Kardex records
   - Credit sales require customer entity relationship for payment tracking
