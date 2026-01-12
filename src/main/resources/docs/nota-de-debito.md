# Nota de débito

## Emitir factura

<mark style="color:green;">`POST`</mark> `https://sandbox.apisunat.pe/api/v3/documents`

**Headers**

| Nombre        | Valor              |
| ------------- | ------------------ |
| Content-Type  | `application/json` |
| Authorization | `Bearer <token>`   |

#### Request Body

{% code fullWidth="false" %}

```json
{
    "documento": "nota_debito",
    "serie": "F001",
    "numero": 1,
    "fecha_de_emision": "2024-09-06",
    "moneda": "PEN",
    "cliente_tipo_de_documento": "6",
    "cliente_numero_de_documento": "20548042535",
    "cliente_denominacion": "INVERSIONES SERVIS S.A.C",
    "cliente_direccion": "AV. DEL PARQUE NORTE NRO. 1126 LIMA - LIMA - SAN BORJA",
    "nota_debito_codigo_tipo": "02",
    "nota_debito_motivo": "Aumento en el valor",
    "documento_afectado": {
        "documento": "factura",
        "serie": "F001",
        "numero": 10
    },
    "items": [
        {
            "unidad_de_medida": "ZZ",
            "descripcion": "PRODUCTO 1 - AUMENTO POR COSTOS DE IMPORTACIÓN",
            "cantidad": "1",
            "valor_unitario": "84.745763",
            "precio_unitario": "100",
            "porcentaje_igv": "18",
            "codigo_tipo_afectacion_igv": "10"
        }
    ],
    "total_gravada": "84.75",
    "total_igv": "15.25",
    "total": "100"
}
```

{% endcode %}

#### Response

{% tabs %}
{% tab title="200 OK " %}
{% code fullWidth="false" %}

```json
{
    "success": true,
    "message": "Nota de débito F001-1 emitida correctamente",
    "payload": {
        "hash": "4HY34uR7a+Vc1Ax3/zrUk1Ng+tjTLwlqa9mRFysy15E=",
        "xml": "http://api.lucode.pe/20123456789-01-FF01-123.xml",
        "cdr": "http://api.lucode.pe/R-20123456789-01-FF01-123.xml",
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
    "message": "ERROR: Documento F001-1 fue emitido anteriormente"
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

<table><thead><tr><th width="262">Name</th><th>Description</th></tr></thead><tbody><tr><td>documento<mark style="color:red;">*</mark></td><td>factura</td></tr><tr><td>serie<mark style="color:red;">*</mark></td><td>serie de 4 dígitos</td></tr><tr><td>numero<mark style="color:red;">*</mark></td><td>correlativo del comprobante</td></tr><tr><td>fecha_de_emision<mark style="color:red;">*</mark></td><td>fecha del día que se emite el comprobante</td></tr><tr><td>fecha_de_vencimiento<mark style="color:red;">*</mark></td><td>fecha cuando se realizará el pago total o la última cuota</td></tr><tr><td>moneda<mark style="color:red;">*</mark></td><td>PEN = soles<br>USD = dólares</td></tr><tr><td>orden_compra_servicio</td><td>número de orden de compra o servicio del cliente</td></tr><tr><td>cliente_tipo_de_documento<mark style="color:red;">*</mark></td><td>1 = DNI<br>6 = RUC</td></tr></tbody></table>

{% hint style="info" %}
**Data requerida por SUNAT:** La anterior tabla muestra los datos mínimos necesarios para la emisión de una factura electrónica, si en caso su empresa desea emitir una factura con otros datos adicionales, por favor comuníquese con nosotros.
{% endhint %}