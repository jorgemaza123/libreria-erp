let carrito = [];
let productosCache = [];

$(document).ready(function() {
    // 1. Cargar productos con formato para BÚSQUEDA INTELIGENTE
    $.get('/ventas/api/productos-activos', function(data) {
        productosCache = data;
        
        // Creamos la data para Select2 incluyendo la categoría en el texto para poder buscarla
        let opciones = data.map(p => ({ 
            id: p.id, 
            text: `${p.nombre} [${p.categoria || 'GRAL'}] - S/ ${p.precioVenta}`,
            producto: p // Guardamos el objeto original
        }));

        // Inicializar Select2
        $('#selectProducto').select2({
            data: opciones,
            placeholder: "Buscar por nombre, categoría o código...",
            allowClear: true,
            width: '85%' // Ajuste visual para que quepa el botón
        });
    });

    // 2. Evento: Al seleccionar un producto de la lista (Mouse o Enter)
    $('#selectProducto').on('select2:select', function (e) {
        let idProd = e.params.data.id;
        agregarAlCarrito(idProd);
        // Limpiar selección para permitir buscar otro inmediatamente
        $('#selectProducto').val(null).trigger('change');
    });

    // 3. Evento: Escáner de Código de Barras (Input superior)
    $('#inputScan').on('keydown', function(e) {
        if (e.key === 'Enter') {
            e.preventDefault();
            let codigo = $(this).val().trim();
            if(codigo) {
                buscarPorCodigo(codigo);
                $(this).val(''); // Limpiar para el siguiente scan
            }
        }
    });
});

// Función para el botón manual [+]
function agregarSeleccionado() {
    let id = $('#selectProducto').val();
    if(id) {
        agregarAlCarrito(id);
        $('#selectProducto').val(null).trigger('change');
    }
}

function buscarPorCodigo(codigo) {
    // Búsqueda exacta por código de barras o interno
    let prod = productosCache.find(p => p.codigoBarra === codigo || p.codigoInterno === codigo);
    if (prod) {
        agregarAlCarrito(prod.id);
    } else {
        Swal.fire('Error', 'Producto no encontrado con ese código', 'error');
    }
}

function agregarAlCarrito(id) {
    // Buscar en la memoria caché (más rápido que ir al servidor)
    let producto = productosCache.find(p => p.id == id);
    
    if (!producto) return;

    // Verificar si ya está en la tabla para sumar cantidad
    let itemExistente = carrito.find(i => i.productoId == id);
    
    if (itemExistente) {
        itemExistente.cantidad++;
    } else {
        // Validación de precio nulo
        let precioFinal = producto.precioVenta ? producto.precioVenta : 0;
        
        carrito.push({
            productoId: producto.id,
            nombre: producto.nombre,
            precio: precioFinal,
            cantidad: 1
        });
    }
    renderizarTabla();
}

function eliminarItem(index) {
    carrito.splice(index, 1);
    renderizarTabla();
}

function renderizarTabla() {
    let html = '';
    let total = 0;

    carrito.forEach((item, index) => {
        let subtotal = item.cantidad * item.precio;
        total += subtotal;
        
        html += `
            <tr>
                <td>${item.nombre}</td>
                <td>S/ ${item.precio.toFixed(2)}</td>
                <td>
                    <input type="number" class="form-control form-control-sm" value="${item.cantidad}" 
                           oninput="actualizarCantidad(${index}, this.value)" style="width: 80px;">
                </td>
                <td class="font-weight-bold">S/ ${subtotal.toFixed(2)}</td>
                <td>
                    <button class="btn btn-danger btn-xs" onclick="eliminarItem(${index})"><i class="fas fa-times"></i></button>
                </td>
            </tr>
        `;
    });

    $('#tablaVentas').html(html);
    calcularTotales(total);
}

function actualizarCantidad(index, cantidad) {
    let cant = parseFloat(cantidad);
    if(cant < 1 || isNaN(cant)) cant = 1; // Evitar ceros o negativos
    
    carrito[index].cantidad = cant;
    
    // Recalcular solo visualmente totales para no redibujar todo el input (perder foco)
    renderizarTabla(); 
}

function calcularTotales(total) {
    let gravada = total / 1.18;
    let igv = total - gravada;

    $('#lblTotal').text(`S/ ${total.toFixed(2)}`);
    $('#lblGravada').text(`S/ ${gravada.toFixed(2)}`);
    $('#lblIgv').text(`S/ ${igv.toFixed(2)}`);
}

function procesarVenta() {
    if(carrito.length === 0) {
        Swal.fire('Atención', 'El carrito está vacío', 'warning');
        return;
    }

    let ventaDTO = {
        tipoComprobante: $('#tipoComprobante').val(),
        clienteDoc: $('#clienteDoc').val() || '00000000',
        clienteNombre: $('#clienteNombre').val() || 'CLIENTE VARIOS',
        clienteDireccion: $('#clienteDireccion').val() || '-',
        clienteTipoDoc: $('#tipoComprobante').val() === 'FACTURA' ? '6' : '1',
        items: carrito
    };

    // Bloquear botón para evitar doble clic
    $('#btnProcesar').prop('disabled', true);

    $.ajax({
        url: '/ventas/api/procesar',
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify(ventaDTO),
        success: function(resp) {
            Swal.fire('Éxito', 'Venta registrada correctamente', 'success').then(() => {
                location.reload();
            });
        },
        error: function(err) {
            $('#btnProcesar').prop('disabled', false);
            let msg = err.responseJSON && err.responseJSON.error ? err.responseJSON.error : 'Error desconocido';
            Swal.fire('Error', 'No se pudo procesar: ' + msg, 'error');
        }
    });
}