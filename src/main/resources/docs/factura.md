# Factura

***

## Emitir Factura Electrónica por API

<mark style="color:green;">`POST`</mark> `https://app.apisunat.pe/api/test/documents`

Cada RUC dispone de un token único para ambos entornos: Desarrollo y Producción. Para obtener más detalles sobre el uso de la API, visite la siguiente página.&#x20;

{% content-ref url="configuracion-api" %}
[configuracion-api](https://docs.apisunat.pe/integracion/facturacion-electronica/configuracion-api)
{% endcontent-ref %}

***

## Ejemplos JSON

{% content-ref url="factura/factura-simple" %}
[factura-simple](https://docs.apisunat.pe/integracion/facturacion-electronica/factura/factura-simple)
{% endcontent-ref %}

{% content-ref url="broken-reference" %}
[Broken link](https://docs.apisunat.pe/integracion/facturacion-electronica/broken-reference)
{% endcontent-ref %}

## Descripción de los datos

{% hint style="info" %}
Todos los campos marcados con un asterisco (<mark style="color:red;">\*</mark>) son obligatorios. Los demás son opcionales y pueden omitirse en el body del envío
{% endhint %}

<table data-full-width="false"><thead><tr><th width="262">Name</th><th>Description</th></tr></thead><tbody><tr><td>documento<mark style="color:red;">*</mark></td><td>factura</td></tr><tr><td>serie<mark style="color:red;">*</mark></td><td>serie de 4 dígitos</td></tr><tr><td>numero<mark style="color:red;">*</mark></td><td>correlativo del comprobante</td></tr><tr><td>fecha_de_emision<mark style="color:red;">*</mark></td><td>fecha del día que se emite el comprobante</td></tr><tr><td>fecha_de_vencimiento<mark style="color:red;">*</mark></td><td>fecha cuando se realizará el pago total o la última cuota</td></tr><tr><td>moneda<mark style="color:red;">*</mark></td><td>PEN = soles<br>USD = dólares</td></tr><tr><td>orden_compra_servicio</td><td>número de orden de compra o servicio del cliente</td></tr><tr><td>cliente_tipo_de_documento<mark style="color:red;">*</mark></td><td>1 = DNI<br>6 = RUC</td></tr></tbody></table>

## Respuestas

{% hint style="info" %}
Los enlaces incluidos en las siguientes respuestas son solo referenciales y se muestran como ejemplos generales. No es posible acceder a ellos directamente. Para visualizar la información, deberá hacerlo a través de nuestras APIs en el ambiente de pruebas utilizando sus propios datos.
{% endhint %}

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
        "pdf": {
            "ticket": "http://api.lucode.pe/pdf/ticket/20123456789-01-FF01-123",
            "a4": "http://api.lucode.pe/pdf/a4/20123456789-01-FF01-123"
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
            "ticket": "http://api.lucode.pe/pdf/ticket/20123456789-01-FF01-123",
            "a4": "http://api.lucode.pe/pdf/a4/20123456789-01-FF01-123"
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

{% embed url="<https://apisunat.lucode.pe/>" %}