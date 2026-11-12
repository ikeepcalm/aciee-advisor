const SHELL_CACHE = 'aciee-shell-v1'
const ASSET_CACHE = 'aciee-assets-v1'
const DATA_CACHE = 'aciee-data-v1'
const KEEP = [SHELL_CACHE, ASSET_CACHE, DATA_CACHE]
const MAX_ASSETS = 150
const PUBLIC_DATA = ['/api/timetable', '/api/catalog/lookups']

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(SHELL_CACHE).then((cache) => cache.add('/')))
  self.skipWaiting()
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((names) =>
        Promise.all(
          names.filter((name) => !KEEP.includes(name)).map((name) => caches.delete(name)),
        ),
      )
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (event) => {
  const request = event.request
  if (request.method !== 'GET') {
    return
  }
  const url = new URL(request.url)
  if (url.origin !== self.location.origin) {
    return
  }
  if (request.mode === 'navigate') {
    if (!/^\/(api|anon|og)\//.test(url.pathname)) {
      event.respondWith(networkFirst(request, SHELL_CACHE, '/'))
    }
  } else if (url.pathname.startsWith('/assets/') || url.pathname.startsWith('/icons/')) {
    event.respondWith(cacheFirst(request))
  } else if (PUBLIC_DATA.includes(url.pathname)) {
    event.respondWith(networkFirst(request, DATA_CACHE))
  }
})

async function networkFirst(request, cacheName, fallbackKey) {
  const cache = await caches.open(cacheName)
  try {
    const response = await fetch(request)
    const isPage = (response.headers.get('content-type') ?? '').includes('text/html')
    if (response.ok && (!fallbackKey || isPage)) {
      await cache.put(fallbackKey ?? request, response.clone())
    }
    return response
  } catch (error) {
    const cached = await cache.match(fallbackKey ?? request)
    if (cached) {
      return cached
    }
    throw error
  }
}

async function cacheFirst(request) {
  const cache = await caches.open(ASSET_CACHE)
  const cached = await cache.match(request)
  if (cached) {
    return cached
  }
  const response = await fetch(request)
  if (response.ok) {
    await cache.put(request, response.clone())
    const keys = await cache.keys()
    await Promise.all(
      keys.slice(0, Math.max(0, keys.length - MAX_ASSETS)).map((key) => cache.delete(key)),
    )
  }
  return response
}
