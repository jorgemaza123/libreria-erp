# Anular Operación

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
    "documento": "nota_credito",
    "serie": "F001",
    "numero": 123,
    "fecha_de_emision": "2025-03-05",
    "moneda": "PEN",
    "cliente_tipo_de_documento": "6",
    "cliente_numero_de_documento": "20494586850",
    "cliente_denominacion": "EMPRESA DE SERVICIOS S.A.C.",
    "cliente_direccion": "CAL.CONTRALMIRANTE MONTERO NRO. 411 MAGDALENA DEL MAR",
    "items": [
        {
            "unidad_de_medida": "ZZ",
            "descripcion": "SERVICIO 01",
            "cantidad": "5810",
            "valor_unitario": "0.084746",
            "porcentaje_igv": "18",
            "codigo_tipo_afectacion_igv": "10",
            "nombre_tributo": "IGV"
        },
        {
            "unidad_de_medida": "ZZ",
            "descripcion": "SERVICIO 02",
            "cantidad": "1569",
            "valor_unitario": "0.084746",
            "porcentaje_igv": "18",
            "codigo_tipo_afectacion_igv": "10",
            "nombre_tributo": "IGV"
        },
        {
            "unidad_de_medida": "ZZ",
            "descripcion": "SERVICIO 03",
            "cantidad": "48",
            "valor_unitario": "0.084746",
            "porcentaje_igv": "18",
            "codigo_tipo_afectacion_igv": "10",
            "nombre_tributo": "IGV"
        }
    ],
    "total": "742.70",
    "nota_credito_codigo_tipo": "01",
    "nota_credito_motivo": "Anulación de la operación",
    "documento_afectado": {
        "documento": "factura",
        "serie": "F001",
        "numero": 128
    }
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
        "xml": "https://apisunat.pe/20123456789-07-F001-123.xml",
        "cdr": "https://apisunat.pe/R-20123456789-07-F001-123.xml",
        "pdf": {
            "ticket": "https://apisunat.pe/pdf/ticket/20123456789-07-F001-123",
            "a4": "https://apisunat.pe/pdf/a4/20123456789-07-F001-123"
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
        "xml": "https://apisunat.pe/20123456789-07-F001-123.xml",
        "cdr": null,
        "pdf": {
            "ticket": "https://apisunat.pe/pdf/ticket/20123456789-07-F001-123",
            "a4": "https://apisunat.pe/pdf/a4/20123456789-07-F001-123"
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
        "xml": "https://apisunat.pe/20123456789-07-F001-123.xml",
        "cdr": "https://apisunat.pe/R-20123456789-07-F001-123.xml",
        "pdf": {
            "ticket": null,
            "a4": null
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