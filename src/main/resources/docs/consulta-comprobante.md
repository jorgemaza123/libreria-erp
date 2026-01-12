# Consulta Comprobante

## API REST

<mark style="color:green;">`POST`</mark>`https://dev.apisunat.pe/api/v1/sunat/comprobante`

**Headers**

| Nombre        | Valor              |
| ------------- | ------------------ |
| Content-Type  | `application/json` |
| Authorization | `Bearer <token>`   |

#### Body

```json
{
    "tipo_comprobante": "01",
    "ruc_emisor": "20100030838",
    "serie": "FLU1",
    "numero": "74647"
}
```

#### Response

```json
{
    "success": true,
    "message": "Detalle de comprobante obtenido correctamente",
    "payload": {
        "emisor": {
            "ruc": "20100030838",
            "razon_social": "G. W. YICHANG & CIA. S.A.",
            "direccion": "JR. CARLOS NEUHAUS RIZO PATRON NRO. 125 URB. CORPAC",
            "departamento": "LIMA",
            "provincia": "LIMA",
            "distrito": "SAN ISIDRO",
            "codigo_ubigeo": "150130",
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
            "serie": "FLU1",
            "numero": "74647",
            "codigo_moneda": "PEN",
            "simbolo_moneda": "S/",
            "descripcion_moneda": "SOLES",
            "fecha_emision": "2025-10-13",
            "fecha_vencimiento": "",
            "observacion": "",
            "glosa": "",
            "orden_compra": "01-SVR-4268284",
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
                "identificacion_interna": "51312",
                "codigo_unidad_medida": "DI",
                "unidad_medida_descripcion": "UNIDAD (BIENES)",
                "descripcion": "MENTOS FRUTA DISP 14X29.04GR 24/1",
                "cantidad": "3.00",
                "valor_unitario": "4.490000",
                "valor_venta": "40.05",
                "valor_isc": "0.00",
                "impuesto_valor": "7.21",
                "impuesto_porcentaje": "18.00",
                "impuesto_codigo_tipo_afectacion": "10",
                "impuesto_codigo_tributo": "1000",
                "impuesto_nombre_tributo": "IGV",
                "impuesto_nombre_tributo_internacional": "VAT",
                "impuesto_valor_otros": "0.00",
                "precio_unitario": "15.75",
                "codigo_precio_unitario": "01",
                "descuentos_aplicados": [
                    {
                        "codigo_descuento": "00",
                        "valor_descuento": "13.290000",
                        "motivo_descuento": "Descuento por ítem",
                        "valor_unitario_regular": "17.780000"
                    }
                ]
            },
            {
                "item": "2",
                "identificacion_interna": "47112",
                "codigo_unidad_medida": "C62",
                "unidad_medida_descripcion": "PIEZAS",
                "descripcion": "FERRERO ROCHER CAJA DORADA T8 10/2",
                "cantidad": "10.00",
                "valor_unitario": "-40.060000",
                "valor_venta": "165.86",
                "valor_isc": "0.00",
                "impuesto_valor": "29.87",
                "impuesto_porcentaje": "18.00",
                "impuesto_codigo_tipo_afectacion": "10",
                "impuesto_codigo_tributo": "1000",
                "impuesto_nombre_tributo": "IGV",
                "impuesto_nombre_tributo_internacional": "VAT",
                "impuesto_valor_otros": "0.00",
                "precio_unitario": "19.57",
                "codigo_precio_unitario": "01",
                "descuentos_aplicados": [
                    {
                        "codigo_descuento": "00",
                        "valor_descuento": "62.940000",
                        "motivo_descuento": "Descuento por ítem",
                        "valor_unitario_regular": "22.880000"
                    }
                ]
            },
            {
                "item": "3",
                "identificacion_interna": "48639",
                "codigo_unidad_medida": "C62",
                "unidad_medida_descripcion": "PIEZAS",
                "descripcion": "FERRERO ROCHER T12x9",
                "cantidad": "6.00",
                "valor_unitario": "-10.270000",
                "valor_venta": "151.43",
                "valor_isc": "0.00",
                "impuesto_valor": "27.26",
                "impuesto_porcentaje": "18.00",
                "impuesto_codigo_tipo_afectacion": "10",
                "impuesto_codigo_tributo": "1000",
                "impuesto_nombre_tributo": "IGV",
                "impuesto_nombre_tributo_internacional": "VAT",
                "impuesto_valor_otros": "0.00",
                "precio_unitario": "29.78",
                "codigo_precio_unitario": "01",
                "descuentos_aplicados": [
                    {
                        "codigo_descuento": "00",
                        "valor_descuento": "42.610000",
                        "motivo_descuento": "Descuento por ítem",
                        "valor_unitario_regular": "32.340000"
                    }
                ]
            },
            {
                "item": "4",
                "identificacion_interna": "47111",
                "codigo_unidad_medida": "DI",
                "unidad_medida_descripcion": "UNIDAD (BIENES)",
                "descripcion": "FERRERO ROCHER BOLSA T3 16/6",
                "cantidad": "1.00",
                "valor_unitario": "80.330000",
                "valor_venta": "80.33",
                "valor_isc": "0.00",
                "impuesto_valor": "14.46",
                "impuesto_porcentaje": "18.00",
                "impuesto_codigo_tipo_afectacion": "10",
                "impuesto_codigo_tributo": "1000",
                "impuesto_nombre_tributo": "IGV",
                "impuesto_nombre_tributo_internacional": "VAT",
                "impuesto_valor_otros": "0.00",
                "precio_unitario": "94.79",
                "codigo_precio_unitario": "01",
                "descuentos_aplicados": [
                    {
                        "codigo_descuento": "00",
                        "valor_descuento": "25.270000",
                        "motivo_descuento": "Descuento por ítem",
                        "valor_unitario_regular": "105.600000"
                    }
                ]
            },
            {
                "item": "5",
                "identificacion_interna": "64006",
                "codigo_unidad_medida": "DI",
                "unidad_medida_descripcion": "UNIDAD (BIENES)",
                "descripcion": "MENTOS 15P MENTA FRESCA DS 10 12/1 30 GR",
                "cantidad": "1.00",
                "valor_unitario": "38.130000",
                "valor_venta": "38.13",
                "valor_isc": "0.00",
                "impuesto_valor": "6.86",
                "impuesto_porcentaje": "18.00",
                "impuesto_codigo_tipo_afectacion": "10",
                "impuesto_codigo_tributo": "1000",
                "impuesto_nombre_tributo": "IGV",
                "impuesto_nombre_tributo_internacional": "VAT",
                "impuesto_valor_otros": "0.00",
                "precio_unitario": "44.99",
                "codigo_precio_unitario": "01",
                "descuentos_aplicados": [
                    {
                        "codigo_descuento": "00",
                        "valor_descuento": "22.770000",
                        "motivo_descuento": "Descuento por ítem",
                        "valor_unitario_regular": "60.900000"
                    }
                ]
            },
            {
                "item": "6",
                "identificacion_interna": "44194",
                "codigo_unidad_medida": "C62",
                "unidad_medida_descripcion": "PIEZAS",
                "descripcion": "HERSHEY'S TAB CHOCOLA C/ALMENDRA 36/12",
                "cantidad": "24.00",
                "valor_unitario": "-93.490000",
                "valor_venta": "67.51",
                "valor_isc": "0.00",
                "impuesto_valor": "12.15",
                "impuesto_porcentaje": "18.00",
                "impuesto_codigo_tipo_afectacion": "10",
                "impuesto_codigo_tributo": "1000",
                "impuesto_nombre_tributo": "IGV",
                "impuesto_nombre_tributo_internacional": "VAT",
                "impuesto_valor_otros": "0.00",
                "precio_unitario": "3.32",
                "codigo_precio_unitario": "01",
                "descuentos_aplicados": [
                    {
                        "codigo_descuento": "00",
                        "valor_descuento": "100.490000",
                        "motivo_descuento": "Descuento por ítem",
                        "valor_unitario_regular": "7.000000"
                    }
                ]
            },
            {
                "item": "7",
                "identificacion_interna": "58526",
                "codigo_unidad_medida": "DI",
                "unidad_medida_descripcion": "UNIDAD (BIENES)",
                "descripcion": "TIC TAC PASTILLAS MENTA 16GR 24DS/12PZ",
                "cantidad": "4.00",
                "valor_unitario": "5.770000",
                "valor_venta": "67.69",
                "valor_isc": "0.00",
                "impuesto_valor": "12.18",
                "impuesto_porcentaje": "18.00",
                "impuesto_codigo_tipo_afectacion": "10",
                "impuesto_codigo_tributo": "1000",
                "impuesto_nombre_tributo": "IGV",
                "impuesto_nombre_tributo_internacional": "VAT",
                "impuesto_valor_otros": "0.00",
                "precio_unitario": "19.97",
                "codigo_precio_unitario": "01",
                "descuentos_aplicados": [
                    {
                        "codigo_descuento": "00",
                        "valor_descuento": "14.870000",
                        "motivo_descuento": "Descuento por ítem",
                        "valor_unitario_regular": "20.640000"
                    }
                ]
            },
            {
                "item": "8",
                "identificacion_interna": "58525",
                "codigo_unidad_medida": "DI",
                "unidad_medida_descripcion": "UNIDAD (BIENES)",
                "descripcion": "TIC TAC PASTILL NARANJA 16GR 24DS/12PZ",
                "cantidad": "4.00",
                "valor_unitario": "5.770000",
                "valor_venta": "67.69",
                "valor_isc": "0.00",
                "impuesto_valor": "12.18",
                "impuesto_porcentaje": "18.00",
                "impuesto_codigo_tipo_afectacion": "10",
                "impuesto_codigo_tributo": "1000",
                "impuesto_nombre_tributo": "IGV",
                "impuesto_nombre_tributo_internacional": "VAT",
                "impuesto_valor_otros": "0.00",
                "precio_unitario": "19.97",
                "codigo_precio_unitario": "01",
                "descuentos_aplicados": [
                    {
                        "codigo_descuento": "00",
                        "valor_descuento": "14.870000",
                        "motivo_descuento": "Descuento por ítem",
                        "valor_unitario_regular": "20.640000"
                    }
                ]
            },
            {
                "item": "9",
                "identificacion_interna": "60248",
                "codigo_unidad_medida": "DI",
                "unidad_medida_descripcion": "UNIDAD (BIENES)",
                "descripcion": "TIC TAC PASTILLAS MENTA INTENSA 24/1",
                "cantidad": "5.00",
                "valor_unitario": "2.070000",
                "valor_venta": "84.63",
                "valor_isc": "0.00",
                "impuesto_valor": "15.23",
                "impuesto_porcentaje": "18.00",
                "impuesto_codigo_tipo_afectacion": "10",
                "impuesto_codigo_tributo": "1000",
                "impuesto_nombre_tributo": "IGV",
                "impuesto_nombre_tributo_internacional": "VAT",
                "impuesto_valor_otros": "0.00",
                "precio_unitario": "19.97",
                "codigo_precio_unitario": "01",
                "descuentos_aplicados": [
                    {
                        "codigo_descuento": "00",
                        "valor_descuento": "18.570000",
                        "motivo_descuento": "Descuento por ítem",
                        "valor_unitario_regular": "20.640000"
                    }
                ]
            },
            {
                "item": "10",
                "identificacion_interna": "64794",
                "codigo_unidad_medida": "DI",
                "unidad_medida_descripcion": "UNIDAD (BIENES)",
                "descripcion": "TIC TAC PASTILL CITRUX 16GR 24DS/12PZ",
                "cantidad": "4.00",
                "valor_unitario": "5.770000",
                "valor_venta": "67.69",
                "valor_isc": "0.00",
                "impuesto_valor": "12.18",
                "impuesto_porcentaje": "18.00",
                "impuesto_codigo_tipo_afectacion": "10",
                "impuesto_codigo_tributo": "1000",
                "impuesto_nombre_tributo": "IGV",
                "impuesto_nombre_tributo_internacional": "VAT",
                "impuesto_valor_otros": "0.00",
                "precio_unitario": "19.97",
                "codigo_precio_unitario": "01",
                "descuentos_aplicados": [
                    {
                        "codigo_descuento": "00",
                        "valor_descuento": "14.870000",
                        "motivo_descuento": "Descuento por ítem",
                        "valor_unitario_regular": "20.640000"
                    }
                ]
            },
            {
                "item": "11",
                "identificacion_interna": "66983",
                "codigo_unidad_medida": "DI",
                "unidad_medida_descripcion": "UNIDAD (BIENES)",
                "descripcion": "TIC TAC FRESA 16G 24DS/12PZ",
                "cantidad": "4.00",
                "valor_unitario": "5.770000",
                "valor_venta": "67.69",
                "valor_isc": "0.00",
                "impuesto_valor": "12.18",
                "impuesto_porcentaje": "18.00",
                "impuesto_codigo_tipo_afectacion": "10",
                "impuesto_codigo_tributo": "1000",
                "impuesto_nombre_tributo": "IGV",
                "impuesto_nombre_tributo_internacional": "VAT",
                "impuesto_valor_otros": "0.00",
                "precio_unitario": "19.97",
                "codigo_precio_unitario": "01",
                "descuentos_aplicados": [
                    {
                        "codigo_descuento": "00",
                        "valor_descuento": "14.870000",
                        "motivo_descuento": "Descuento por ítem",
                        "valor_unitario_regular": "20.640000"
                    }
                ]
            },
            {
                "item": "12",
                "identificacion_interna": "64739",
                "codigo_unidad_medida": "BX",
                "unidad_medida_descripcion": "UNIDAD (BIENES)",
                "descripcion": "BAUDUCCO WAFER DE CHOCOLATE 24/140G",
                "cantidad": "1.00",
                "valor_unitario": "36.250000",
                "valor_venta": "36.25",
                "valor_isc": "0.00",
                "impuesto_valor": "6.53",
                "impuesto_porcentaje": "18.00",
                "impuesto_codigo_tipo_afectacion": "10",
                "impuesto_codigo_tributo": "1000",
                "impuesto_nombre_tributo": "IGV",
                "impuesto_nombre_tributo_internacional": "VAT",
                "impuesto_valor_otros": "0.00",
                "precio_unitario": "42.78",
                "codigo_precio_unitario": "01",
                "descuentos_aplicados": [
                    {
                        "codigo_descuento": "00",
                        "valor_descuento": "42.950000",
                        "motivo_descuento": "Descuento por ítem",
                        "valor_unitario_regular": "79.200000"
                    }
                ]
            },
            {
                "item": "13",
                "identificacion_interna": "67903",
                "codigo_unidad_medida": "BX",
                "unidad_medida_descripcion": "UNIDAD (BIENES)",
                "descripcion": "BAUDUCCO COOKIES ORIGINAL PZX60G 24/1",
                "cantidad": "1.00",
                "valor_unitario": "43.480000",
                "valor_venta": "43.48",
                "valor_isc": "0.00",
                "impuesto_valor": "7.83",
                "impuesto_porcentaje": "18.00",
                "impuesto_codigo_tipo_afectacion": "10",
                "impuesto_codigo_tributo": "1000",
                "impuesto_nombre_tributo": "IGV",
                "impuesto_nombre_tributo_internacional": "VAT",
                "impuesto_valor_otros": "0.00",
                "precio_unitario": "51.31",
                "codigo_precio_unitario": "01",
                "descuentos_aplicados": [
                    {
                        "codigo_descuento": "00",
                        "valor_descuento": "66.920000",
                        "motivo_descuento": "Descuento por ítem",
                        "valor_unitario_regular": "110.400000"
                    }
                ]
            },
            {
                "item": "14",
                "identificacion_interna": "67903",
                "codigo_unidad_medida": "C62",
                "unidad_medida_descripcion": "PIEZAS",
                "descripcion": "BAUDUCCO COOKIES ORIGINAL PZX60G 24/1",
                "cantidad": "8.00",
                "valor_unitario": "0.000000",
                "valor_venta": "36.80",
                "valor_isc": "0.00",
                "impuesto_valor": "0.00",
                "impuesto_porcentaje": "18.00",
                "impuesto_codigo_tipo_afectacion": "31",
                "impuesto_codigo_tributo": "9996",
                "impuesto_nombre_tributo": "GRA",
                "impuesto_nombre_tributo_internacional": "FRE",
                "impuesto_valor_otros": "0.00",
                "precio_unitario": "4.60",
                "codigo_precio_unitario": "02",
                "descuentos_aplicados": []
            }
        ],
        "totales": {
            "total_grav_oner": "978.43",
            "total_inaf_oner": "0.00",
            "total_valor_venta_exonerado": "0.00",
            "total_valor_venta_opera_gratuitas": "36.80",
            "total_exportacion": "0.00",
            "total_descuentos": "0.00",
            "total_anticipos": "0.00",
            "total_isc": "0.00",
            "total_otros_cargos": "0.00",
            "total_otros_tributos": "0.00",
            "total_igv": "176.12",
            "total_redondeo": "0.00",
            "total_valor_venta": "978.43",
            "descuento_global_afecta_base_imponible": "0.00",
            "descuento_global_no_afecta_base_imponible": "0.00",
            "monto_total_general": "1154.55"
        },
        "url_descarga": {
            "pdf": "https://apisunat.pe/rce/document/pdf/20100030838-01-FLU1-74647",
            "xml": "https://apisunat.pe/rce/document/xml/20100030838-01-FLU1-74647"
        }
    }
}
```