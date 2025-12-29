let carrito = [];
let productosCache = [];

$(document).ready(function() {
    // Cargar productos con formato enriquecido para BÚSQUEDA INTELIGENTE CLIENTE
    $.get('/ventas/api/productos-activos', function(data) {
        productosCache = data;
        // Creamos un texto compuesto: "Nombre - Categoría - Código"
        // Así Select2 filtrará por cualquiera de esos datos
        let opciones = data.map(p => ({ 
            id: p.id, 
            text: `${p.nombre} [${p.categoria || 'GENERAL'}] - S/${p.precioVenta}`,
            producto: p // Guardamos el objeto completo para usarlo luego
        }));
        
        $('#selectProducto').select2({ 
            data: opciones,
            placeholder: "Buscar por nombre, categoría o código...",
            allowClear: true,
            width: '85%'
        });
    });

    // Evento al seleccionar producto
    $('#selectProducto').on('select2:select', function (e) {
        let idProd = e.params.data.id;
        agregarItem(idProd);
        $('#selectProducto').val(null).trigger('change');
    });
});

function agregarSeleccionado() {
    let id = $('#selectProducto').val();
    if(id) {
        agregarItem(id);
        $('#selectProducto').val(null).trigger('change');
    }
}

function agregarItem(id) {
    let prod = productosCache.find(p => p.id == id);
    if(!prod) return;

    if(carrito.find(i => i.productoId == id)) {
        Swal.fire('Aviso', 'El producto ya está en la lista', 'info');
        return;
    }

    // Si es nuevo, costo es 0. Si ya tiene historial, su ultimo precio compra.
    let costoInicial = prod.precioCompra ? prod.precioCompra : 0;

    carrito.push({
        productoId: prod.id,
        nombre: prod.nombre,
        cantidad: 1,
        costoUnitario: costoInicial
    });
    renderizar();
}

function renderizar() {
    let html = '';
    let total = 0;

    carrito.forEach((item, index) => {
        let cant = parseFloat(item.cantidad) || 0;
        let costo = parseFloat(item.costoUnitario) || 0;
        let subtotal = cant * costo;
        total += subtotal;

        html += `
            <tr>
                <td>${item.nombre}</td>
                <td>
                    <input type="number" step="0.01" class="form-control form-control-sm" 
                           value="${costo}" oninput="actualizarLinea(${index}, 'costo', this.value)">
                </td>
                <td>
                    <input type="number" class="form-control form-control-sm" 
                           value="${item.cantidad}" oninput="actualizarLinea(${index}, 'cant', this.value)">
                </td>
                <td class="font-weight-bold">S/ ${subtotal.toFixed(2)}</td>
                <td><button class="btn btn-danger btn-xs" onclick="eliminar(${index})"><i class="fas fa-trash"></i></button></td>
            </tr>
        `;
    });

    $('#tablaCompras').html(html);
    $('#lblTotal').text('S/ ' + total.toFixed(2));
}

function actualizarLinea(index, tipo, valor) {
    let val = parseFloat(valor);
    if(isNaN(val)) val = 0;

    if(tipo === 'cant') carrito[index].cantidad = val;
    if(tipo === 'costo') carrito[index].costoUnitario = val;
    
    // IMPORTANTE: No llamamos a renderizar() completo aquí para no perder el foco del input
    // Solo actualizamos el subtotal visual de esa fila y el total global
    let item = carrito[index];
    let subtotal = item.cantidad * item.costoUnitario;
    
    // Actualizar texto del subtotal en la fila actual (suponiendo que es la celda 3)
    let fila = document.getElementById('tablaCompras').rows[index];
    if(fila) fila.cells[3].innerText = "S/ " + subtotal.toFixed(2);

    // Recalcular Total Global
    let totalGlobal = carrito.reduce((sum, i) => sum + (i.cantidad * i.costoUnitario), 0);
    $('#lblTotal').text('S/ ' + totalGlobal.toFixed(2));
}

function eliminar(index) {
    carrito.splice(index, 1);
    renderizar();
}

function guardarCompra() {
    if(carrito.length === 0) {
        Swal.fire('Error', 'No hay productos en la lista', 'error');
        return;
    }
    
    if(!$('#serieDoc').val() || !$('#numDoc').val() || !$('#rucProv').val()) {
        Swal.fire('Atención', 'Faltan datos de la Factura (Serie, Número, RUC)', 'warning');
        return;
    }

    let compraDTO = {
        tipoComprobante: $('#tipoDoc').val(),
        serie: $('#serieDoc').val(),
        numero: $('#numDoc').val(),
        fechaEmision: new Date().toISOString().split('T')[0],
        proveedorRuc: $('#rucProv').val(),
        proveedorRazon: $('#razonProv').val(),
        items: carrito
    };

    $.ajax({
        url: '/compras/api/procesar',
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify(compraDTO),
        success: function(resp) {
            Swal.fire('Éxito', 'Inventario Actualizado', 'success')
                .then(() => location.reload());
        },
        error: function(xhr) {
            Swal.fire('Error', 'Error al guardar: ' + xhr.status, 'error');
        }
    });
}