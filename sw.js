// Service worker mínimo: permite instalar la app desde Chrome. No guarda nada en caché,
// así siempre se ven los datos más recientes.
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', e => e.waitUntil(self.clients.claim()));
self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET') return;
  e.respondWith(fetch(e.request));
});
