const CACHE_NAME = 'parandiplaan-v1';
const STATIC_ASSETS = [
    '/',
    '/index.html',
    '/login.html',
    '/register.html',
    '/dashboard.html',
    '/vault.html',
    '/settings.html',
    '/offline.html',
    '/assets/style.css',
    '/assets/auth.css',
    '/assets/dashboard.css',
    '/assets/vault.css',
    '/assets/settings.css',
    '/assets/contacts.css',
    '/assets/onboarding.css',
    '/assets/admin.css',
    '/assets/session-guard.js',
    '/assets/i18n.js',
    '/assets/analytics.js',
    '/assets/i18n/et.json',
    '/assets/i18n/en.json',
    '/favicon.svg'
];

// Install — cache static assets
self.addEventListener('install', function(event) {
    event.waitUntil(
        caches.open(CACHE_NAME).then(function(cache) {
            return cache.addAll(STATIC_ASSETS);
        })
    );
    self.skipWaiting();
});

// Activate — clean old caches
self.addEventListener('activate', function(event) {
    event.waitUntil(
        caches.keys().then(function(names) {
            return Promise.all(
                names.filter(function(name) { return name !== CACHE_NAME; })
                     .map(function(name) { return caches.delete(name); })
            );
        })
    );
    self.clients.claim();
});

// Fetch — network-first for API, cache-first for static assets
self.addEventListener('fetch', function(event) {
    var url = new URL(event.request.url);

    // API calls: network-first
    if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/actuator/')) {
        event.respondWith(
            fetch(event.request).catch(function() {
                return new Response(JSON.stringify({ error: 'Offline' }), {
                    status: 503,
                    headers: { 'Content-Type': 'application/json' }
                });
            })
        );
        return;
    }

    // Static assets: cache-first, then network
    event.respondWith(
        caches.match(event.request).then(function(cached) {
            if (cached) return cached;
            return fetch(event.request).then(function(response) {
                // Cache successful GET responses
                if (response.ok && event.request.method === 'GET') {
                    var clone = response.clone();
                    caches.open(CACHE_NAME).then(function(cache) {
                        cache.put(event.request, clone);
                    });
                }
                return response;
            }).catch(function() {
                // Offline fallback for navigation requests
                if (event.request.mode === 'navigate') {
                    return caches.match('/offline.html');
                }
                return new Response('Offline', { status: 503 });
            });
        })
    );
});
