# Consultar estado

<mark style="color:green;">`POST`</mark> `https://sandbox.apisunat.pe/api/v3/status`

### **Headers**

| Nombre        | Valor              |
| ------------- | ------------------ |
| Content-Type  | `application/json` |
| Authorization | `Bearer <token>`   |

### Request Body

{% code fullWidth="false" %}

```json
{
    "documento": "factura",
    "serie": "F001",
    "numero": 123
}
```

{% endcode %}

### Response

{% tabs %}
{% tab title="200 OK " %}
{% code fullWidth="false" %}

```json
{
    "success": true,
    "message": "Factura FF01-44 se encuentra registrado en SUNAT.",
    "payload": {
        "hash": "4HY34uR7a+Vc1Ax3/zrUk1Ng+tjTLwlqa9mRFysy15E=",
        "xml": "http://api.lucode.pe/20123456789-01-F001-123.xml",
        "cdr": "http://api.lucode.pe/R-20123456789-01-F001-123.xml",
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

{% tab title="400 Bad request" %}
{% code title="Documento duplicado" overflow="wrap" %}

```json
{
    "success": false,
    "message": "Factura F001-123 no se encuentra registrado."
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
{% endtabs %}

### Descripción de datos

<table><thead><tr><th width="262">Name</th><th>Description</th></tr></thead><tbody><tr><td>documento<mark style="color:red;">*</mark></td><td><code>factura</code><br><code>boleta</code><br><code>nota_credito</code><br><code>nota_debito</code></td></tr><tr><td>serie<mark style="color:red;">*</mark></td><td>serie de 4 dígitos del comprobante a consultar</td></tr><tr><td>numero<mark style="color:red;">*</mark></td><td>correlativo del comprobante a consultar</td></tr></tbody></table>