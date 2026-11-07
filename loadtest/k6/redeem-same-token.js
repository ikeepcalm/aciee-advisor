import http from 'k6/http'
import { Counter } from 'k6/metrics'
import { BASE } from './common.js'

const token = open(__ENV.TOKEN_FILE || '/scripts/token.json')
const created = new Counter('redeem_created')
const conflict = new Counter('redeem_conflict')
const other = new Counter('redeem_other')

export const options = {
  scenarios: {
    same_token: { executor: 'per-vu-iterations', vus: Number(__ENV.VUS || 200), iterations: 1, maxDuration: '1m' },
  },
  thresholds: {
    redeem_created: ['count==1'],
    redeem_other: ['count==0'],
  },
}

export default function () {
  const res = http.post(`${BASE}/anon/submissions`, token, { headers: { 'Content-Type': 'application/json' } })
  if (res.status === 201) created.add(1)
  else if (res.status === 409) conflict.add(1)
  else other.add(1)
}
