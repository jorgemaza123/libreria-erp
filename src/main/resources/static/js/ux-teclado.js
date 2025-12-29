/**
 * UX TECLADO:
 * - Enter navega al siguiente input en lugar de enviar el formulario (submit).
 * - Autofocus inteligente.
 */
document.addEventListener('DOMContentLoaded', function() {
    
    // Buscar el primer input visible y darle foco
    const primerInput = document.querySelector('input:not([type="hidden"]):not([disabled])');
    if (primerInput) {
        primerInput.focus();
    }

    // Interceptar tecla ENTER en formularios
    const forms = document.querySelectorAll('form');
    
    forms.forEach(form => {
        const inputs = Array.from(form.querySelectorAll('input, select, textarea, button'));
        
        form.addEventListener('keydown', function(e) {
            if (e.key === 'Enter') {
                const target = e.target;
                
                // Si es un textarea o el botón de submit, dejar comportamiento normal
                if (target.tagName === 'TEXTAREA' || target.type === 'submit') {
                    return;
                }

                e.preventDefault(); // Evitar submit
                
                const index = inputs.indexOf(target);
                if (index > -1 && index < inputs.length - 1) {
                    const nextElement = inputs[index + 1];
                    nextElement.focus();
                    // Si es input texto, seleccionar todo el contenido
                    if(nextElement.tagName === 'INPUT') {
                        nextElement.select();
                    }
                }
            }
        });
    });
});