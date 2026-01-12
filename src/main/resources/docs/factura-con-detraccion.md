# Factura con detracción

### Endpoint

<mark style="color:green;">`POST`</mark> `https://sandbox.apisunat.pe/api/v3/documents`

### Header

Es obligatorio incluir el encabezado `Authorization` en cada solicitud.\
El **token es único por empresa** y puedes obtenerlo ingresando a [https://app.apisunat.pe](https://app.apisunat.pe/), en el módulo de **Organizaciones**.

```powershell
header 'Authorization: Bearer {TOKEN}'
```

### Request Body

{% code fullWidth="false" %}

```json
{
    "documento": "factura",
    "serie": "F001",
    "numero": 123,
    "fecha_de_emision": "2025-01-01",
    "moneda": "PEN",
    "tipo_operacion": "1001", // Usar este tipo para esta operación 
    "cliente_tipo_de_documento": "6",
    "cliente_numero_de_documento": "20123456789",
    "cliente_denominacion": "EMPRESA DE SERVICIOS S.A.C",
    "cliente_direccion": "AV. SAN MARTIN - LIMA",
    "items": [
        {
            "unidad_de_medida": "NIU",
            "descripcion": "Nombre del producto",
            "cantidad": "1",
            "valor_unitario": "850.151246", //Se recomienda usar 6 decimales
            "porcentaje_igv": "18",
            "codigo_tipo_afectacion_igv": "10",
            "nombre_tributo": "IGV"
        }
    ],
    "detraccion": {
        "detraccion_tipo": "020",
        "detraccion_porcentaje": "12", 
        "detraccion_total": "102.00", //Aproximado al entero menor
        "medio_de_pago": "001",
        "numero_cuenta_banco_nacion": "00001234567"
    },
    "cuotas": [
        {
            "importe": "883.18", //No considerar el monto de la detracción
            "fecha_de_pago": "2025-01-20"
        }
    ],
    "total": "1003.18"
}
```

{% endcode %}

### Response

{% tabs %}
{% tab title="200 OK" %}

<pre class="language-json" data-title="El comprobante está firmado y aceptado por SUNAT" data-full-width="false"><code class="lang-json">{
    "success": true,
    "message": "El comprobante fue enviado y aceptado por SUNAT.",
    "payload": {
<strong>        "estado": "ACEPTADO",
</strong>        "hash": "4HY34uR7a+Vc1Ax3/zrUk1Ng+tjTLwlqa9mRFysy15E=",
        "xml": "https://apisunat.pe/20123456789-01-B001-123.xml",
        "cdr": "https://apisunat.pe/R-20123456789-01-B001-123.xml",
        "pdf": {
            "ticket": "https://apisunat.pe/pdf/ticket/20123456789-03-B001-123"
        }
    }
}
</code></pre>

<pre class="language-json" data-title="El comprobante está firmado y pendiente de la aceptación de SUNAT"><code class="lang-json">{
    "success": true,
    "message": "El comprobante aún está siendo procesado por SUNAT, consulte su estado en unos minutos.",
    "payload": {
<strong>        "estado": "PENDIENTE",
</strong>        "hash": "4HY34uR7a+Vc1Ax3/zrUk1Ng+tjTLwlqa9mRFysy15E=",
        "xml": "https://apisunat.pe/20123456789-01-B001-123.xml",
        "cdr": null,
        "pdf": {
            "ticket": "https://apisunat.pe/pdf/ticket/20123456789-01-B001-123"
        }
    }
}
</code></pre>

<pre class="language-json" data-title="El comprobante fue rechazado por SUNAT debido a datos incorrectos" data-full-width="false"><code class="lang-json">{
    "success": true,
    "message": "El comprobante presenta errores o datos incorrectos",
    "payload": {
<strong>        "estado": "RECHAZADO",
</strong>        "hash": "4HY34uR7a+Vc1Ax3/zrUk1Ng+tjTLwlqa9mRFysy15E=",
        "xml": "https://apisunat.pe/20123456789-01-B001-123.xml",
        "cdr": "https://apisunat.pe/R-20123456789-01-B001-123.xml",
        "pdf": {
            "ticket": "https://apisunat.pe/pdf/ticket/20123456789-01-B001-123"
        }
    }
}
</code></pre>

{% endtab %}

{% tab title="422 Error en datos" %}

```json
{
    "success": false,
    "message": "El campo documento es requerido"
}
```

{% endtab %}

{% tab title="400 Error al procesar" %}
{% code title="Documento duplicado" overflow="wrap" %}

```json
{
    "success": false,
    "message": "ERROR: boleta B002-1 fue emitido anteriormente."
}
```

{% endcode %}
{% endtab %}

{% tab title="401 Error de permisos" %}

```json
{
    "success": false,
    "message": "Acceso no autorizado."
}
```

{% endtab %}
{% endtabs %}