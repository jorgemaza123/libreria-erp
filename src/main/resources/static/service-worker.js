/**
 * Service Worker — Sistema ERP PWA
 *
 * Estrategia:
 *   - Assets estáticos (CSS, JS, fonts, imágenes): Cache-First (rápido)
 *   - Páginas HTML / API: Network-First (datos frescos, fallback a cache)
 *   - Si todo falla: offline.html
 *
 * El SW no intenta funcionar offline completo (requiere BD PostgreSQL).
 * Su propósito: instalación como app, carga rápida, experiencia nativa.
 */

const CACHE_NAME = 'sistema-erp-v1';
const OFFLINE_URL = '/offline.html';

// Assets estáticos a pre-cachear en install
const PRECACHE_ASSETS = [
    OFFLINE_URL,
    '/favicon.ico',
    '/images/logo.png'
];

// =============================================
// INSTALL: Pre-cachear assets críticos
// =============================================
self.addEventListener('install', function(event) {
    event.waitUntil(
        caches.open(CACHE_NAME).then(function(cache) {
            return cache.addAll(PRECACHE_ASSETS);
        }).then(function() {
            return self.skipWaiting();
        })
    );
});

// =============================================
// ACTIVATE: Limpiar caches antiguos
// =============================================
self.addEventListener('activate', function(event) {
    event.waitUntil(
        caches.keys().then(function(cacheNames) {
            return Promise.all(
                cacheNames
                    .filter(function(name) { return name !== CACHE_NAME; })
                    .map(function(name) { return caches.delete(name); })
            );
        }).then(function() {
            return self.clients.claim();
        })
    );
});

// =============================================
// FETCH: Estrategia por tipo de recurso
// =============================================
self.addEventListener('fetch', function(event) {
    var request = event.request;

    // Solo interceptar GET
    if (request.method !== 'GET') return;

    var url = new URL(request.url);

    // No interceptar extensiones de Chrome, etc.
    if (!url.protocol.startsWith('http')) return;

    // Assets estáticos: Cache-First
    if (isStaticAsset(url)) {
        event.respondWith(cacheFirst(request));
        return;
    }

    // Páginas y API: Network-First
    event.respondWith(networkFirst(request));
});

// =============================================
// ESTRATEGIAS DE CACHE
// =============================================

/**
 * Cache-First: busca en cache, si no existe va a la red y guarda.
 * Ideal para assets que cambian poco (CSS, JS, fonts, imágenes CDN).
 */
function cacheFirst(request) {
    return caches.match(request).then(function(cached) {
        if (cached) return cached;

        return fetch(request).then(function(response) {
            if (response && response.status === 200) {
                var responseClone = response.clone();
                caches.open(CACHE_NAME).then(function(cache) {
                    cache.put(request, responseClone);
                });
            }
            return response;
        }).catch(function() {
            // Asset no disponible
            return new Response('', { status: 404 });
        });
    });
}

/**
 * Network-First: intenta red, si falla usa cache, si no hay cache → offline.
 * Ideal para páginas HTML y datos que necesitan estar frescos.
 */
function networkFirst(request) {
    return fetch(request).then(function(response) {
        // Solo cachear respuestas exitosas de navegación
        if (response && response.status === 200 && request.mode === 'navigate') {
            var responseClone = response.clone();
            caches.open(CACHE_NAME).then(function(cache) {
                cache.put(request, responseClone);
            });
        }
        return response;
    }).catch(function() {
        return caches.match(request).then(function(cached) {
            if (cached) return cached;

            // Si es navegación, mostrar página offline
            if (request.mode === 'navigate') {
                return caches.match(OFFLINE_URL);
            }

            return new Response('', { status: 503 });
        });
    });
}

// =============================================
// HELPERS
// =============================================

/**
 * Determina si una URL es un asset estático cacheable.
 */
function isStaticAsset(url) {
    var path = url.pathname;
    var host = url.hostname;

    // CDNs conocidos (AdminLTE, Font Awesome, DataTables, Select2, Google Fonts)
    if (host.includes('cdn.') || host.includes('cdnjs.') ||
        host.includes('fonts.googleapis.com') || host.includes('fonts.gstatic.com')) {
        return true;
    }

    // Assets locales por extensión
    if (/\.(css|js|woff2?|ttf|eot|svg|png|jpg|jpeg|gif|ico|json)$/i.test(path)) {
        // Excluir manifest.json y API endpoints
        if (path === '/manifest.json') return false;
        if (path.startsWith('/conexion-movil/api/')) return false;
        if (path.startsWith('/api/')) return false;
        return true;
    }

    // Carpetas estáticas conocidas
    if (path.startsWith('/css/') || path.startsWith('/js/') || path.startsWith('/img/') ||
        path.startsWith('/images/') || path.startsWith('/plugins/') || path.startsWith('/dist/') ||
        path.startsWith('/webjars/') || path.startsWith('/uploads/')) {
        return true;
    }

    return false;
}
