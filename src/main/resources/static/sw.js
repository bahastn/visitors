/* Service worker: caches the app shell so the reception desk keeps working when the network drops. */
const CACHE = 'vms-v3';
const SHELL = [
  '/offline.html', '/css/theme.css', '/css/app.css', '/js/app.js', '/js/reception.js', '/img/logo-64.png',
  '/vendor/bootstrap/bootstrap.min.css', '/vendor/bootstrap/bootstrap.rtl.min.css', '/vendor/bootstrap/bootstrap.bundle.min.js',
  '/vendor/bootstrap-icons/bootstrap-icons.min.css', '/vendor/html5-qrcode/html5-qrcode.min.js'
];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== location.origin) return;
  // Static files: cache first
  if (url.pathname.startsWith('/vendor/') || url.pathname.startsWith('/css/') || url.pathname.startsWith('/js/') || url.pathname.startsWith('/img/')) {
    e.respondWith(caches.match(req).then(r => r || fetch(req).then(res => {
      const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); return res;
    })));
    return;
  }
  // Pages: network first, keep a copy of the reception page and mobile page for offline use
  if (req.mode === 'navigate') {
    e.respondWith(fetch(req).then(res => {
      if (url.pathname === '/reception' || url.pathname === '/m') {
        const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy));
      }
      return res;
    }).catch(() => caches.match(req).then(r => r || caches.match('/offline.html'))));
  }
});
