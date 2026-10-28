import type { LocationQuery } from 'vue-router'
import ro from '@/i18n/ro.json'

export class ApiError extends Error {
  status: number
  problem: Record<string, unknown>

  constructor(status: number, problem: Record<string, unknown>) {
    super(String(problem.detail ?? status))
    this.status = status
    this.problem = problem
  }
}

function xsrfToken(): string | undefined {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/)
  return match?.[1] && decodeURIComponent(match[1])
}

export async function api<T = void>(method: string, path: string, body?: unknown): Promise<T> {
  if (method !== 'GET' && !xsrfToken()) {
    await fetch('/api/auth/csrf')
  }
  const response = await fetch(path, {
    method,
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': xsrfToken() ?? '' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) {
    throw new ApiError(response.status, await response.json().catch(() => ({})))
  }
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

type Translate = (key: string, params: Record<string, unknown>) => string

export function describeError(t: Translate, error: unknown): string {
  const detail = error instanceof ApiError ? String(error.problem.detail) : ''
  return Object.hasOwn(ro.auth.errors, detail) ? t(`auth.errors.${detail}`, (error as ApiError).problem) : t('auth.errors.generic', {})
}

export function safeNext(query: LocationQuery): string {
  const next = query.next
  return typeof next === 'string' && next.startsWith('/') && !next.startsWith('//') ? next : '/'
}
