import http from 'k6/http'

export const BASE = __ENV.BASE_URL || 'http://host.docker.internal:8081'

export function signIn(email, password) {
  const csrf = http.get(`${BASE}/api/auth/csrf`).cookies['XSRF-TOKEN'][0].value
  const res = http.post(`${BASE}/api/auth/signin`, JSON.stringify({ email, password }), {
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrf },
    cookies: { 'XSRF-TOKEN': csrf },
  })
  if (res.status !== 200) throw new Error(`sign-in failed: ${res.status}`)
  return res.cookies.SESSION[0].value
}
