# Boleta de exportación

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
    "moneda": "USD",
    "tipo_operacion": "0200",
    "cliente_tipo_de_documento": "B",
    "cliente_numero_de_documento": "123456",
    "cliente_denominacion": "NOMBRE PERSONA",
    "cliente_direccion": "DIRECCIÓN",
    "items": [
        {
            "unidad_de_medida": "ZZ",
            "descripcion": "Nombre del servicio",
            "cantidad": "1",
            "valor_unitario": "200",
            "porcentaje_igv": "0",
            "codigo_tipo_afectacion_igv": "40",
            "nombre_tributo": "EXP"
        }
    ],
    "total": "200"
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
    "message": "Boleta B001-123 emitida correctamente",
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

{% endcode %}
{% endtab %}

{% tab title="200 PENDIENTE" %}

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