# Reporte de Compras Detallada

## API REST

<mark style="color:green;">`GET`</mark> `https://dev.apisunat.pe/api/v1/sunat/rce?period=202512&page=1`

**Headers**

| Nombre        | Valor              |
| ------------- | ------------------ |
| Content-Type  | `application/json` |
| Authorization | `Bearer <token>`   |

Filters (Query Params)

| Nombre          | Descripción                                                                                                                                                    | Ejemplo       |
| --------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------- |
| `period`        | Año y mes                                                                                                                                                      | `202502`      |
| `page`          | Número de página                                                                                                                                               | `1`           |
| `start_date`    | yyyy-mm-dd                                                                                                                                                     | `2025-12-01`  |
| `end_date`      | yyyy-mm-dd                                                                                                                                                     | `2025-12-22`  |
| `document_type` | <p>Tipo de comprobante<br><code>01</code>: Factura</p><p><code>03</code>: Boletas<br><code>07</code>: Notas de Crédito<br><code>08</code>: Notas de Débito</p> | `07`          |
| `document_ruc`  | RUC del emisor del comprobante                                                                                                                                 | `20123456789` |

#### Response

```json
{
    "success": true,
    "message": "Reporte de compras detallado obtenido correctamente",
    "payload": {
        "paginate": {
            "page": 1,
            "total_pages": 102,
            "per_page": 50,
            "items": 50,
            "total_items": 2056
        },
        "downloads_summary": {
            "completed": 20056,
            "pending": 90,
            "total": 20146
        },
        "items": [
            {
                "emisor": {
                    "ruc": "10061488176",
                    "razon_social": "AGUILA ULLOA EFRAIN VICTOR",
                    "direccion": "AV. CARR. CARRETERA CENTRAL S SN APV. CARAZ COSTADO MAYORISTA TRUJILLO",
                    "departamento": "HUAYLAS",
                    "provincia": "ANCASH",
                    "distrito": "CARAZ",
                    "codigo_ubigeo": "",
                    "codigo_pais": "PE"
                },
                "cliente": {
                    "numero_documento": "22629629942",
                    "nombre_cliente": "EMPRESA PRUEBA S.A.C.",
                    "direccion": "JR. LIMA 123"
                },
                "detalle": {
                    "tipo_comprobante": "01",
                    "nombre_comprobante": "Factura Electrónica",
                    "serie": "E001",
                    "numero": "88",
                    "codigo_moneda": "PEN",
                    "simbolo_moneda": "S/",
                    "descripcion_moneda": "SOLES",
                    "fecha_emision": "2025-12-01",
                    "fecha_vencimiento": "",
                    "observacion": "",
                    "glosa": "",
                    "orden_compra": "",
                    "forma_pago": "Contado",
                    "cuotas": [],
                    "detraccion": null,
                    "retencion": null,
                    "estado_comprobante": "Aceptado",
                    "codigo_estado_comprobante": "1",
                    "cargos_aplicados": [],
                    "descuentos_aplicados": [],
                    "documentos_relacionados": []
                },
                "items": [
                    {
                        "item": "1",
                        "identificacion_interna": "",
                        "codigo_unidad_medida": "NIU",
                        "unidad_medida_descripcion": "UNIDAD (BIENES)",
                        "descripcion": "POR CONSUMO DE ALIMENTOS DEL 01-11-25 AL 30-11-25",
                        "cantidad": "1.00",
                        "valor_unitario": "438.980000",
                        "valor_venta": "438.98",
                        "valor_isc": "0.00",
                        "impuesto_valor": "79.02",
                        "impuesto_porcentaje": "18.00",
                        "impuesto_codigo_tipo_afectacion": "10",
                        "impuesto_codigo_tributo": "1000",
                        "impuesto_nombre_tributo": "IGV",
                        "impuesto_nombre_tributo_internacional": "VAT",
                        "impuesto_valor_otros": "0.00",
                        "precio_unitario": "518.00",
                        "codigo_precio_unitario": "01",
                        "descuentos_aplicados": []
                    }
                ],
                "totales": {
                    "total_grav_oner": "438.98",
                    "total_inaf_oner": "0.00",
                    "total_valor_venta_exonerado": "0.00",
                    "total_valor_venta_opera_gratuitas": "0.00",
                    "total_exportacion": "0.00",
                    "total_descuentos": "0.00",
                    "total_anticipos": "0.00",
                    "total_isc": "0.00",
                    "total_otros_cargos": "0.00",
                    "total_otros_tributos": "0.00",
                    "total_igv": "79.02",
                    "total_redondeo": "0.00",
                    "total_valor_venta": "438.98",
                    "descuento_global_afecta_base_imponible": "0.00",
                    "descuento_global_no_afecta_base_imponible": "0.00",
                    "monto_total_general": "518.00"
                },
                "url_descarga": {
                    "pdf": "https://apisunat.pe/rce/document/pdf/10061488176-01-E001-88",
                    "xml": "https://apisunat.pe/rce/document/xml/10061488176-01-E001-88"
                }
            },
            {
                "emisor": {
                    "ruc": "10080275973",
                    "razon_social": "REYES MARIÑOS DE ZEGARRA YSABEL",
                    "direccion": "CALLE CINCO# 505 BLOCK 9 DEP 501 LIMA-LIMA RIMAC",
                    "departamento": "",
                    "provincia": "",
                    "distrito": "",
                    "codigo_ubigeo": "",
                    "codigo_pais": ""
                },
                "cliente": {
                    "numero_documento": "22629629942",
                    "nombre_cliente": "EMPRESA PRUEBA S.A.C.",
                    "direccion": "JR. LIMA 123"
                },
                "detalle": {
                    "tipo_comprobante": "01",
                    "nombre_comprobante": "Factura Electrónica",
                    "serie": "FF01",
                    "numero": "693",
                    "codigo_moneda": "PEN",
                    "simbolo_moneda": "S/",
                    "descripcion_moneda": "SOLES",
                    "fecha_emision": "2025-12-01",
                    "fecha_vencimiento": "",
                    "observacion": "",
                    "glosa": "",
                    "orden_compra": "",
                    "forma_pago": "Contado",
                    "cuotas": [],
                    "detraccion": null,
                    "retencion": null,
                    "estado_comprobante": "Aceptado",
                    "codigo_estado_comprobante": "1",
                    "cargos_aplicados": [],
                    "descuentos_aplicados": [],
                    "documentos_relacionados": []
                },
                "items": [
                    {
                        "item": "1",
                        "identificacion_interna": "PRE2776",
                        "codigo_unidad_medida": "NIU",
                        "unidad_medida_descripcion": "UNIDAD (BIENES)",
                        "descripcion": "PREZLET ORIGINAL BOLSA 250 GR",
                        "cantidad": "20.00",
                        "valor_unitario": "4.237288",
                        "valor_venta": "84.75",
                        "valor_isc": "0.00",
                        "impuesto_valor": "15.25",
                        "impuesto_porcentaje": "18.00",
                        "impuesto_codigo_tipo_afectacion": "10",
                        "impuesto_codigo_tributo": "1000",
                        "impuesto_nombre_tributo": "IGV",
                        "impuesto_nombre_tributo_internacional": "VAT",
                        "impuesto_valor_otros": "0.00",
                        "precio_unitario": "5.00",
                        "codigo_precio_unitario": "01",
                        "descuentos_aplicados": []
                    },
                    {
                        "item": "2",
                        "identificacion_interna": "PRE4010",
                        "codigo_unidad_medida": "NIU",
                        "unidad_medida_descripcion": "UNIDAD (BIENES)",
                        "descripcion": "PREZLET PICANTE BOLSA 40 GR",
                        "cantidad": "90.00",
                        "valor_unitario": "0.847458",
                        "valor_venta": "76.27",
                        "valor_isc": "0.00",
                        "impuesto_valor": "13.73",
                        "impuesto_porcentaje": "18.00",
                        "impuesto_codigo_tipo_afectacion": "10",
                        "impuesto_codigo_tributo": "1000",
                        "impuesto_nombre_tributo": "IGV",
                        "impuesto_nombre_tributo_internacional": "VAT",
                        "impuesto_valor_otros": "0.00",
                        "precio_unitario": "1.00",
                        "codigo_precio_unitario": "01",
                        "descuentos_aplicados": []
                    },
                    {
                        "item": "3",
                        "identificacion_interna": "PER4721",
                        "codigo_unidad_medida": "NIU",
                        "unidad_medida_descripcion": "UNIDAD (BIENES)",
                        "descripcion": "PERU NACHO TORTILLA CHIPS BOLSA 90 GR",
                        "cantidad": "40.00",
                        "valor_unitario": "3.813559",
                        "valor_venta": "152.54",
                        "valor_isc": "0.00",
                        "impuesto_valor": "27.46",
                        "impuesto_porcentaje": "18.00",
                        "impuesto_codigo_tipo_afectacion": "10",
                        "impuesto_codigo_tributo": "1000",
                        "impuesto_nombre_tributo": "IGV",
                        "impuesto_nombre_tributo_internacional": "VAT",
                        "impuesto_valor_otros": "0.00",
                        "precio_unitario": "4.50",
                        "codigo_precio_unitario": "01",
                        "descuentos_aplicados": []
                    },
                    {
                        "item": "4",
                        "identificacion_interna": "PER7525",
                        "codigo_unidad_medida": "NIU",
                        "unidad_medida_descripcion": "UNIDAD (BIENES)",
                        "descripcion": "PERU NACHO MULTIGRAIN BOLSA 90 GR",
                        "cantidad": "40.00",
                        "valor_unitario": "4.067797",
                        "valor_venta": "162.71",
                        "valor_isc": "0.00",
                        "impuesto_valor": "29.29",
                        "impuesto_porcentaje": "18.00",
                        "impuesto_codigo_tipo_afectacion": "10",
                        "impuesto_codigo_tributo": "1000",
                        "impuesto_nombre_tributo": "IGV",
                        "impuesto_nombre_tributo_internacional": "VAT",
                        "impuesto_valor_otros": "0.00",
                        "precio_unitario": "4.80",
                        "codigo_precio_unitario": "01",
                        "descuentos_aplicados": []
                    },
                    {
                        "item": "5",
                        "identificacion_interna": "ACT4833",
                        "codigo_unidad_medida": "NIU",
                        "unidad_medida_descripcion": "UNIDAD (BIENES)",
                        "descripcion": "ACT II. POP CORN NATURAL SOBRE 80 GR",
                        "cantidad": "16.00",
                        "valor_unitario": "3.050847",
                        "valor_venta": "48.81",
                        "valor_isc": "0.00",
                        "impuesto_valor": "8.79",
                        "impuesto_porcentaje": "18.00",
                        "impuesto_codigo_tipo_afectacion": "10",
                        "impuesto_codigo_tributo": "1000",
                        "impuesto_nombre_tributo": "IGV",
                        "impuesto_nombre_tributo_internacional": "VAT",
                        "impuesto_valor_otros": "0.00",
                        "precio_unitario": "3.60",
                        "codigo_precio_unitario": "01",
                        "descuentos_aplicados": []
                    },
                    {
                        "item": "6",
                        "identificacion_interna": "VAL2684",
                        "codigo_unidad_medida": "NIU",
                        "unidad_medida_descripcion": "UNIDAD (BIENES)",
                        "descripcion": "VALPER PAPEL ALUMINIO 25 FT",
                        "cantidad": "24.00",
                        "valor_unitario": "5.932203",
                        "valor_venta": "142.37",
                        "valor_isc": "0.00",
                        "impuesto_valor": "25.63",
                        "impuesto_porcentaje": "18.00",
                        "impuesto_codigo_tipo_afectacion": "10",
                        "impuesto_codigo_tributo": "1000",
                        "impuesto_nombre_tributo": "IGV",
                        "impuesto_nombre_tributo_internacional": "VAT",
                        "impuesto_valor_otros": "0.00",
                        "precio_unitario": "7.00",
                        "codigo_precio_unitario": "01",
                        "descuentos_aplicados": []
                    }
                ],
                "totales": {
                    "total_grav_oner": "667.46",
                    "total_inaf_oner": "0.00",
                    "total_valor_venta_exonerado": "0.00",
                    "total_valor_venta_opera_gratuitas": "0.00",
                    "total_exportacion": "0.00",
                    "total_descuentos": "0.00",
                    "total_anticipos": "0.00",
                    "total_isc": "0.00",
                    "total_otros_cargos": "0.00",
                    "total_otros_tributos": "0.00",
                    "total_igv": "120.14",
                    "total_redondeo": "0.00",
                    "total_valor_venta": "667.46",
                    "descuento_global_afecta_base_imponible": "0.00",
                    "descuento_global_no_afecta_base_imponible": "0.00",
                    "monto_total_general": "787.60"
                },
                "url_descarga": {
                    "pdf": "https://apisunat.pe/rce/document/pdf/10080275973-01-FF01-693",
                    "xml": "https://apisunat.pe/rce/document/xml/10080275973-01-FF01-693"
                }
            }
        ]
    }
}
```