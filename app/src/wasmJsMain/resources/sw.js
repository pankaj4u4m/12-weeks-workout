// Minimal service worker for TwelveWeek — network-first with a runtime
// cache fallback so the app keeps working offline after the first visit.
// Doesn't precache a fixed file list on purpose: the wasmJs build's JS/Wasm
// filenames are content-hashed and change every deploy, so a static
// precache list would go stale; caching whatever's actually fetched avoids
// that entirely at the cost of the very first load needing a network hit.
const CACHE_NAME = 'twelveweek-v1';

self.addEventListener('install', (event) => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((names) =>
      Promise.all(names.filter((n) => n !== CACHE_NAME).map((n) => caches.delete(n)))
    )
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') return;
  const url = new URL(event.request.url);
  if (url.origin !== self.location.origin) return;

  event.respondWith(
    fetch(event.request)
      .then((response) => {
        if (response && response.ok) {
          const clone = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone));
        }
        return response;
      })
      .catch(() => caches.match(event.request))
  );
});
