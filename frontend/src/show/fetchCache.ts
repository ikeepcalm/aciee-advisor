type Canned = (url: URL) => unknown
export const NOT_FOUND = Symbol('not found')

let inflight = 0
let installed = false
const cache = new Map<string, Promise<{ status: number; body: string }>>()
const canned: [RegExp, Canned][] = []

export function cannedResponse(pattern: RegExp, body: Canned) {
  canned.push([pattern, body])
}

export function clearCache() {
  cache.clear()
}

export function pendingRequests() {
  return inflight
}

// Every GET from the app is answered once from the network and then from memory, so a beat replays without waiting.
export function installFetchCache() {
  if (installed) {
    return
  }
  installed = true
  const network = window.fetch.bind(window)
  window.fetch = async (input, init) => {
    const method = (init?.method ?? (input instanceof Request ? input.method : 'GET')).toUpperCase()
    const url = new URL(input instanceof Request ? input.url : String(input), location.origin)
    if (
      method !== 'GET' ||
      url.origin !== location.origin ||
      !/^\/(api|anon)\//.test(url.pathname) ||
      url.pathname.startsWith('/api/show/')
    ) {
      return network(input, init)
    }
    const key = url.pathname + url.search
    let entry = cache.get(key)
    if (!entry) {
      const match = canned.find(([pattern]) => pattern.test(key))
      entry = match
        ? Promise.resolve(answer(match[1](url)))
        : network(input, init).then(async (response) => ({
            status: response.status,
            body: await response.text(),
          }))
      cache.set(key, entry)
      entry.catch(() => cache.delete(key))
    }
    inflight++
    try {
      const { status, body } = await entry
      if (status >= 500) {
        cache.delete(key)
      }
      return new Response(body || null, { status, headers: { 'Content-Type': 'application/json' } })
    } finally {
      inflight--
    }
  }
}

function answer(value: unknown) {
  return value === NOT_FOUND
    ? { status: 404, body: '{}' }
    : { status: 200, body: JSON.stringify(value) }
}
