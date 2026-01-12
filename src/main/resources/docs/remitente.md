# GR Remitente

**Ambiente de pruebas:**

&#x20;       <mark style="color:green;">`POST`</mark> `https://app.apisunat.pe/api/v3/dispatches`

***

**Headers**

| Nombre        | Valor              |
| ------------- | ------------------ |
| Content-Type  | `application/json` |
| Authorization | `Bearer <token>`   |

#### Request Body

{% code fullWidth="false" %}

```json

{
    "documento": "guia_remision_remitente",
    "serie": "T001",
    "numero": "1",
    "fecha_de_emision": "2025-12-17",
    "hora_de_emision": "17:22:00",
    "modalidad_de_transporte": "02",
    "motivo_de_traslado": "01",
    "fecha_inicio_de_traslado": "2025-12-17",
    "destinatario_tipo_de_documento": "6",
    "destinatario_numero_de_documento": "20609286696",
    "destinatario_denominacion": "EMPRESA SOCIEDAD ANONIMA CERRADA",
    "destinatario_direccion": "Cal. General Mendiburu Nro. 855",
    "punto_de_partida_ubigeo": "150132",
    "punto_de_partida_direccion": "AV. LURIGANCHO NRO. 626 AZCARRUZ BAJO - SAN JUAN DE LURIGANCHO",
    "punto_de_llegada_ubigeo": "150122",
    "punto_de_llegada_direccion": "Cal. General Mendiburu Nro. 855",
    "peso_bruto_total": "1.9",
    "peso_bruto_unidad_de_medida": "KGM",
    "numero_de_bultos": 1,
    "observaciones": null,
    "documentos_relacionados": [
        {
            "documento": "factura",
            "serie": "F001",
            "numero": "3108",
            "ruc_emisor": "20553300429"
        }
    ],
    "transportista": {
        "ruc": "20512528458",
        "denominacion": "Shalom Empresarial S.A.C.",
        "numero_registro_MTC": "123",
        "numero_autorizacion": "123",
        "codigo_entidad_autorizadora": "123"
    },
    "conductores": [
        {
            "conductor": "principal",
            "tipo_de_documento": "1",
            "numero_de_documento": "47101979",
            "nombres": "JUAN VINICIO",
            "apellidos": "GONZALES MACEDO",
            "numero_licencia_conducir": "Q43101919"
        }
    ],
    "vehiculos": [
        {
            "vehiculo": "principal",
            "numero_de_placa": "F6Y931"
        }
    ],
    "items": [
        {
            "codigo_interno": "P002",
            "descripcion": "DISCO DE MOLEDORA 18",
            "unidad_de_medida": "NIU",
            "cantidad": 1
        }
    ]
}
```

{% endcode %}

Response

{% tabs %}
{% tab title="200 OK " %}
{% code fullWidth="false" %}

```json
{
    "success": true,
    "message": "Guía de Remision T001-1 generado correctamente",
    "payload": {
        "hash": "4HY34uR7a+Vc1Ax3/zrUk1Ng+tjTLwlqa9mRFysy15E=",
        "xml": "https://apisunat.pe/20123456789-01-FF01-123.xml",
        "cdr": "https://apisunat.pe/R-20123456789-01-FF01-123.xml",
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
    "message": "ERROR: Documento T001-123 fue emitido anteriormente"
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