import http from 'k6/http'
import { check } from 'k6'
import { BASE, signIn } from './common.js'

export const options = {
  scenarios: {
    students: {
      executor: 'ramping-arrival-rate',
      startRate: 20,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 400,
      stages: [
        { target: Number(__ENV.PEAK_RATE || 300), duration: '30s' },
        { target: Number(__ENV.PEAK_RATE || 300), duration: '30s' },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{page:course}': ['p(95)<500'],
    'http_req_duration{page:professor}': ['p(95)<500'],
    'http_req_duration{page:catalogue}': ['p(95)<500'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
}

export function setup() {
  const session = signIn('demo.student@student.ugal.ro', 'demo-parola')
  const plans = http.get(`${BASE}/api/catalog/specializations`).json()
    .filter((s) => s.code.startsWith('FX'))
    .flatMap((s) => s.plans.map((p) => p.id))
  const years = plans.flatMap((plan) => [1, 2, 3, 4].map((year) => `${BASE}/api/catalog/plans/${plan}/years/${year}`))
  const items = years.slice(0, 40).flatMap((url) => http.get(url).json().map((item) => item.id))
  const slugs = items.slice(0, 30).flatMap((id) =>
    http.get(`${BASE}/api/catalog/courses/${id}`, { cookies: { SESSION: session } }).json().teachers.map((t) => t.slug))
  return { session, years, items, slugs: [...new Set(slugs)] }
}

const pick = (list) => list[Math.floor(Math.random() * list.length)]

export default function ({ session, years, items, slugs }) {
  const params = (page) => ({ cookies: { SESSION: session }, tags: { page } })
  const roll = Math.random()
  const res = roll < 0.6
    ? http.get(`${BASE}/api/catalog/courses/${pick(items)}`, params('course'))
    : roll < 0.9
      ? http.get(`${BASE}/api/professors/${pick(slugs)}`, params('professor'))
      : http.get(pick(years), params('catalogue'))
  check(res, { 'status 200': (r) => r.status === 200 })
}
