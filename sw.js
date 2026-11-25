/* Service worker LOOKA.
   Задача скромная и честная: открыть сайт, даже если связи нет, и не мешать обновлениям.
   Стратегия — «сеть вперёд, кэш в запас»: всегда берём свежую страницу, а если
   сети нет, отдаём последнюю сохранённую. Так человек никогда не увидит старую
   версию при живом интернете. */
const CACHE = 'looka-v1';
const SHELL = ['./', './index.html', './manifest.json', './icon-192.png', './icon-512.png'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting()).catch(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(caches.keys()
    .then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;                       /* запросы к ИИ и аккаунту не трогаем */
  const url = new URL(req.url);
  if (url.origin !== location.origin) return;             /* чужие домены — мимо кэша */
  if (url.pathname.indexOf('/ai') >= 0) return;
  e.respondWith(
    fetch(req)
      .then(r => { const copy = r.clone(); caches.open(CACHE).then(c => c.put(req, copy)).catch(()=>{}); return r; })
      .catch(() => caches.match(req).then(r => r || caches.match('./index.html')))
  );
});
