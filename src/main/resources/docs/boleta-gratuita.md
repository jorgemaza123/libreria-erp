# Boleta gratuita

### Endpoint

<mark style="color:green;">`POST`</mark> `https://sandbox.apisunat.pe/api/v3/documents`

### Header

Es obligatorio incluir el encabezado `Authorization` en cada solicitud.\
El **token es único por empresa** y puedes obtenerlo ingresando a [https://app.apisunat.pe](https://app.apisunat.pe/?utm_source=chatgpt.com), en el módulo de **Organizaciones**.

```powershell
header 'Authorization: Bearer {TOKEN}'
```

### Request Body

{% code fullWidth="false" %}

```json
{
    "documento": "boleta",
    "serie": "B001",
    "numero": 123,
    "fecha_de_emision": "2025-01-03",
    "moneda": "PEN",
    "tipo_operacion": "0101",
    "cliente_tipo_de_documento": "6",
    "cliente_numero_de_documento": "20123456789",
    "cliente_denominacion": "EMPRESA DE SERVICIOS S.A.C",
    "cliente_direccion": "AV. SAN MARTIN - LIMA",
    "items": [
        {
            "unidad_de_medida": "NIU",
            "descripcion": "Nombre del producto",
            "cantidad": "1",
            "valor_unitario": "234",
            "porcentaje_igv": "18",
            "codigo_tipo_afectacion_igv": "11",
            "nombre_tributo": "GRA"
        }
    ],
    "total": "0"
}
```

{% endcode %}

{% hint style="info" %}

### Operaciones GRATUITAS

En operaciones gratuitas, se debe **registrar de forma referencial** el valor del producto y su impuesto (por ejemplo, IGV 18%) como si fuera una venta normal.

⚠️ El valor de la venta e impuesto **no se suma al campo `total`**, solo se incluye en el comprobante para fines informativos y control de SUNAT.
{% endhint %}

### Response

{% tabs %}
{% tab title="200 ACEPTADO" %}
{% code fullWidth="false" %}

```json
{
    "success": true,
    "message": "Boleta B001-123 emitida correctamente",
    "payload": {
        "estado": "PENDIENTE",
        "hash": "4HY34uR7a+Vc1Ax3/zrUk1Ng+tjTLwlqa9mRFysy15E=",
        "xml": "http://api.lucode.pe/20123456789-03-B001-123.xml",
        "cdr": null,
        "pdf": {
            "ticket": "http://api.lucode.pe/pdf/ticket/20123456789-03-B001-123"
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
    "message": "Factura F001-123 emitida correctamente",
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
    "message": "ERROR: Documento B001-123 fue emitido anteriormente"
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