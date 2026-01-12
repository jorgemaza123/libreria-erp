/**
 * Configuración global de AJAX para incluir el token CSRF en todas las peticiones
 * Este archivo debe ser incluido en todas las páginas que realicen peticiones AJAX
 */
(function() {
    'use strict';

    // Función para obtener el token CSRF del meta tag
    function getCsrfToken() {
        return document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
    }

    // Función para obtener el header del CSRF
    function getCsrfHeader() {
        return document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
    }

    // Configurar jQuery AJAX si está disponible
    if (typeof $ !== 'undefined' && $.ajaxSetup) {
        $.ajaxSetup({
            beforeSend: function(xhr) {
                const token = getCsrfToken();
                const header = getCsrfHeader();
                if (token && header) {
                    xhr.setRequestHeader(header, token);
                }
            }
        });
    }

    // Configurar Fetch API global
    const originalFetch = window.fetch;
    window.fetch = function(url, options = {}) {
        const token = getCsrfToken();
        const header = getCsrfHeader();

        if (token && header) {
            options.headers = options.headers || {};
            if (options.headers instanceof Headers) {
                options.headers.append(header, token);
            } else {
                options.headers[header] = token;
            }
        }

        return originalFetch(url, options);
    };

    // Configurar XMLHttpRequest
    const originalOpen = XMLHttpRequest.prototype.open;
    const originalSend = XMLHttpRequest.prototype.send;

    XMLHttpRequest.prototype.open = function() {
        this._url = arguments[1];
        return originalOpen.apply(this, arguments);
    };

    XMLHttpRequest.prototype.send = function() {
        const token = getCsrfToken();
        const header = getCsrfHeader();

        if (token && header && this._url && !this._url.startsWith('http')) {
            this.setRequestHeader(header, token);
        }

        return originalSend.apply(this, arguments);
    };

    console.log('CSRF protection enabled for AJAX requests');
})();
