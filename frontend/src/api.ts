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
  const match = document.cookie.match(/(?:^|; )ACIEE-XSRF=([^;]*)/)
  return match?.[1] && decodeURIComponent(match[1])
}

export async function api<T = void>(method: string, path: string, body?: unknown): Promise<T> {
  if (method !== 'GET' && !xsrfToken()) {
    await fetch('/api/auth/csrf')
  }
  return parse<T>(
    await fetch(path, {
      method,
      headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': xsrfToken() ?? '' },
      body: body === undefined ? undefined : JSON.stringify(body),
    }),
  )
}

export async function anonApi<T = void>(
  method: string,
  path: string,
  body?: unknown,
  token?: string,
): Promise<T> {
  return parse<T>(
    await fetch(path, {
      method,
      credentials: 'omit',
      headers: { 'Content-Type': 'application/json', ...(token ? { 'X-Token': token } : {}) },
      body: body === undefined ? undefined : JSON.stringify(body),
    }),
  )
}

async function parse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new ApiError(response.status, await response.json().catch(() => ({})))
  }
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export interface Option {
  code: string
  label: string
  group: string | null
}

export interface Lookups {
  axes: Option[]
  pillars: Option[]
  examFormats: Option[]
  attendance: Option[]
  reportReasons: Option[]
  moderationReasons: Option[]
  threshold: number
}

let lookupsRequest: Promise<Lookups> | null = null

export function lookups(): Promise<Lookups> {
  lookupsRequest ??= api<Lookups>('GET', '/api/catalog/lookups').catch((e) => {
    lookupsRequest = null
    throw e
  })
  return lookupsRequest
}

type Translate = (key: string, params: Record<string, unknown>) => string

export function describeError(t: Translate, error: unknown): string {
  const detail = error instanceof ApiError ? String(error.problem.detail) : ''
  return Object.hasOwn(ro.errors, detail)
    ? t(`errors.${detail}`, (error as ApiError).problem)
    : t('errors.generic', {})
}

export function safeNext(query: LocationQuery, fallback = '/'): string {
  const next = query.next
  return typeof next === 'string' && next.startsWith('/') && !next.startsWith('//')
    ? next
    : fallback
}

// Microsoft sign-in leaves the SPA, so the page to return to waits in this tab's storage.
export function rememberNext(query: LocationQuery) {
  try {
    sessionStorage.setItem('signin-next', safeNext(query))
  } catch {}
}

export function takeNext(): string {
  try {
    const next = sessionStorage.getItem('signin-next')
    sessionStorage.removeItem('signin-next')
    return safeNext({ next })
  } catch {
    return '/'
  }
}

export interface PublicComment {
  id: string
  body: string
  week: string
}

export interface ProfessorSubject {
  subjectId: number
  subjectName: string
  itemId: number
  components: string
  firstYear: number
  lastYear: number
  radar: { n: number; scores: (number | null)[] } | null
  comments: PublicComment[]
}

export interface ProfessorPage {
  fullName: string
  title: string
  department: string
  archived: boolean
  threshold: number
  ratings: number
  overall: number | null
  teaching: number | null
  practice: number | null
  grading: number | null
  bioRo: string | null
  bioEn: string | null
  photo: string | null
  cvUrl: string | null
  radar: { n: number; scores: (number | null)[] } | null
  subjects: ProfessorSubject[]
}

export interface ProfessorListItem {
  slug: string
  fullName: string
  title: string
  department: string
  subjects: number
  ratings: number
  overall: number | null
  photo: string | null
}

export type WeekParity = 'ALL' | 'ODD' | 'EVEN'

export interface Lesson {
  day: number
  start: string
  end: string
  weeks: WeekParity
  groups: string[]
  kind: string | null
  subject: string
  room: string | null
  teacher: string | null
  note: string | null
  lines: string[]
  course: string
  courseId: number | null
}

export interface TimetableProgram {
  code: string
  year: number
  name: string
  title: string
  semester: string
  pdfUrl: string
  updatedAt: string | null
  groups: string[]
  lessons: Lesson[]
  weeks: Record<string, Exclude<WeekParity, 'ALL'>>
}

export interface Timetable {
  checkedAt: string | null
  source: string
  programs: TimetableProgram[]
}

export interface TimetableChange {
  change: 'added' | 'removed'
  detectedAt: string
  day: number
  startTime: string
  endTime: string
  weeks: WeekParity
  kind: string | null
  subject: string
  room: string | null
  teacher: string | null
}
