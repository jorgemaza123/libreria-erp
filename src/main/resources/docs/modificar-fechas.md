# Modificar Fechas

### Endpoint

<mark style="color:green;">`POST`</mark> `https://sandbox.apisunat.pe/api/v3/documents`

### Header

Es obligatorio incluir el encabezado `Authorization` en cada solicitud.\
El **token es único por empresa** y puedes obtenerlo ingresando a [https://app.apisunat.pe](https://app.apisunat.pe/?utm_source=chatgpt.com), en el módulo de **Organizaciones**.

```powershell
header 'Authorization: Bearer {TOKEN}'
```

### Request Body

{% hint style="info" %}
Este tipo de nota de crédito no modifica los importes ni la estructura de la factura original, únicamente define un nuevo cronograma de pago asociado al comprobante afectado. Por este motivo, los importes en la nota deben registrarse en cero, excepto en el detalle de las cuotas, donde se debe consignar la información correspondiente a los montos originales de la factura referenciada.
{% endhint %}

{% code fullWidth="false" %}

```json
{
    "documento": "nota_credito",
    "serie": "F001",
    "numero": 123,
    "fecha_de_emision": "2025-09-10",
    "fecha_de_vencimiento": "2025-09-10",
    "moneda": "PEN",
    "orden_compra_servicio": null,
    "cliente_tipo_de_documento": "6",
    "cliente_numero_de_documento": "2012356789",
    "cliente_denominacion": "EMPRESA PROVEEDOR S.A.C.",
    "cliente_direccion": "JR. PRIMAVERA NRO 123 - CERCADO DE LIMA",
    "nota_credito_codigo_tipo": "13", //** Importante **
    "nota_credito_motivo": "Corrección de fecha de vencimiento y cuotas",
    "documento_afectado": {
        "documento": "factura",
        "serie": "F001",
        "numero": 197
    },
    "items": [
        {
            "unidad_de_medida": "ZZ",
            "descripcion": "SERVICIO DE MANTENIMIENTO",
            "cantidad": "1",
            "valor_unitario": "0",
            "precio_unitario": "0",
            "porcentaje_igv": "18",
            "codigo_tipo_afectacion_igv": "10"
        }
    ],
    // Establecer las nuevas cuotas y fechas
    "cuotas": [
        {
            "importe": "236",
            "fecha_de_pago": "2025-09-27"
        }
    ],
    "total_gravada": "0",
    "total_igv": "0",
    "total": "0"
}
```

{% endcode %}

### Response

{% tabs %}
{% tab title="200 ACEPTADO" %}
{% code fullWidth="false" %}

```json
{
    "success": true,
    "message": "Nota de Crédito F001-123 emitida correctamente",
    "payload": {
        "estado": "ACEPTADO",
        "hash": "4HY34uR7a+Vc1Ax3/zrUk1Ng+tjTLwlqa9mRFysy15E=",
        "xml": "http://api.lucode.pe/20123456789-01-FF01-123.xml",
        "cdr": "http://api.lucode.pe/R-20123456789-01-FF01-123.xml",
        "pdf": {
            "ticket": "http://api.lucode.pe/pdf/ticket/20123456789-01-FF01-123"
        }
    }
}
```

{% endcode %}
{% endtab %}

{% tab title="200 PENDIENTE" %}

```json
{
    "success": true,
    "message": "Nota de Crédito F001-123 emitida correctamente",
    "payload": {
        "estado": "PENDIENTE",
        "hash": "4HY34uR7a+Vc1Ax3/zrUk1Ng+tjTLwlqa9mRFysy15E=",
        "xml": "http://api.lucode.pe/20123456789-01-FF01-123.xml",
        "cdr": null,
        "pdf": {
            "ticket": "http://api.lucode.pe/pdf/ticket/20123456789-01-FF01-123"
        }
    }
}
```

{% endtab %}

{% tab title="400 Bad request" %}
{% code title="Documento duplicado" overflow="wrap" %}

```json
{
    "success": false,
    "message": "ERROR: Documento F001-123 fue emitido anteriormente"
}
```

{% endcode %}

{% code title="Datos requeridos" overflow="wrap" %}

```json
{
    "success": false,
    "message": "JSON no estructurado correctamente (Validación capa 01)",
    "payload": {
        "documento": [
            "El campo documento es requerido."
        ]
    }
}
```

{% endcode %}
{% endtab %}

{% tab title="401 Permission denied" %}

```json
{
    "success": false,
    "message": "Error en la autenticación, token no válido."
}
```

{% endtab %}
{% endtabs %}