# Boleta descuento item

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
    "cliente_tipo_de_documento": "1",
    "cliente_numero_de_documento": "75413811",
    "cliente_denominacion": "NOMBRE DEL CLIENTE",
    "cliente_direccion": "AV. SAN MARTIN - LIMA",
    "items": [
        {
            "unidad_de_medida": "NIU",
            "descripcion": "Nombre del producto",
            "cantidad": "2",
            "valor_unitario": "234.125457", //Se recomienda usar 6 decimales
            "porcentaje_igv": "18",
            "codigo_tipo_afectacion_igv": "10",
            "nombre_tributo": "IGV",
            "descuentos": [
                {
                    "monto": "5",
                    "codigo": "00" //Según el Catálogo N°53
                }
            ]
        }
    ],
    "total": "546.64"
}
```

{% endcode %}

### Catálogo SUNAT N°53&#x20;

Códigos de cargos, descuentos y otras deducciones

<table><thead><tr><th width="82">Código</th><th width="531">Descripción</th><th>Nivel</th></tr></thead><tbody><tr><td>00</td><td>Descuentos que afectan la base imponible del IGV/IVAP</td><td>item</td></tr><tr><td>01</td><td>Descuentos que no afectan la base imponible del IGV/IVAP</td><td>item</td></tr><tr><td>07</td><td>Factor de compensación - Decreto de urgencia N. 010-2004</td><td>item</td></tr><tr><td>47</td><td>Cargos que afectan la base imponible del IGV/IVAP</td><td>item</td></tr><tr><td>48</td><td>Cargos que no afectan la base imponible del IGV/IVAP</td><td>item</td></tr><tr><td>54</td><td>Factor de aportación - Decreto de urgencia N. 010-2004</td><td>item</td></tr></tbody></table>

### Response

{% tabs %}
{% tab title="200 OK" %}

<pre class="language-json" data-title="El comprobante está firmado y aceptado por SUNAT" data-full-width="false"><code class="lang-json">{
    "success": true,
    "message": "Boleta B001-123 emitida correctamente",
    "payload": {
<strong>        "estado": "ACEPTADO",
</strong>        "hash": "4HY34uR7a+Vc1Ax3/zrUk1Ng+tjTLwlqa9mRFysy15E=",
        "xml": "http://api.lucode.pe/20123456789-03-B001-123.xml",
        "cdr": "http://api.lucode.pe/R-20123456789-03-B001-123.xml",
        "pdf": {
            "ticket": "http://api.lucode.pe/pdf/ticket/20123456789-03-B001-123"
        }
    }
}
</code></pre>

<pre class="language-json" data-title="El comprobante está firmado y pendiente de la aceptación de SUNAT"><code class="lang-json">{
    "success": true,
    "message": "Boleta B001-123 emitida correctamente",
    "payload": {
<strong>        "estado": "PENDIENTE",
</strong>        "hash": "4HY34uR7a+Vc1Ax3/zrUk1Ng+tjTLwlqa9mRFysy15E=",
        "xml": "http://api.lucode.pe/20123456789-03-B001-123.xml",
        "cdr": null,
        "pdf": {
            "ticket": "http://api.lucode.pe/pdf/ticket/20123456789-03-B001-123"
        }
    }
}
</code></pre>

<pre class="language-json" data-title="El comprobante fue rechazado por SUNAT debido a datos incorrectos" data-full-width="false"><code class="lang-json">{
    "success": true,
    "message": "Boleta B001-123 emitida correctamente",
    "payload": {
<strong>        "estado": "RECHAZADO",
</strong>        "hash": "4HY34uR7a+Vc1Ax3/zrUk1Ng+tjTLwlqa9mRFysy15E=",
        "xml": "http://api.lucode.pe/20123456789-03-B001-123.xml",
        "cdr": "http://api.lucode.pe/R-20123456789-03-B001-123.xml",
        "pdf": {
            "ticket": "http://api.lucode.pe/pdf/ticket/20123456789-03-B001-123"
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