# Anular Boleta

### Endpoint

<mark style="color:green;">`POST`</mark> `https://sandbox.apisunat.pe/api/v3/daily-summary`

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
    "documento": "resumen_diario",
    "documentos_afectados": [
        {
            "accion_resumen": "anular",
            "documento": "boleta",
            "serie": "B001",
            "numero": "1"
        }
    ]
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
    "message": "Factura F001-123 emitida correctamente",
    "payload": {
        "estado": "ACEPTADO",
        "hash": "4HY34uR7a+Vc1Ax3/zrUk1Ng+tjTLwlqa9mRFysy15E=",
        "xml": "http://api.lucode.pe/20123456789-01-FF01-123.xml",
        "cdr": "http://api.lucode.pe/R-20123456789-01-FF01-123.xml",
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
        "cdr": null
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