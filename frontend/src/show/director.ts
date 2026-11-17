import { nextTick, reactive, shallowRef, type Component } from 'vue'
import router from '@/router'
import { pendingRequests } from './fetchCache'

const HEADER = 72

export const stage = reactive({
  cursor: { x: innerWidth / 2, y: innerHeight * 0.6, visible: false, down: false, ms: 0 },
  caption: null as null | { title: string; body?: string; at: 'top' | 'bottom' },
  spot: null as null | Element,
  scene: shallowRef<Component | null>(null),
  sceneTake: 0,
  pace: 1,
})

export class Skipped extends Error {}

let generation = 0
let abort = new AbortController()

export function setPace(pace: number) {
  stage.pace = pace
}

export function beginRun() {
  abort = new AbortController()
  return ++generation
}

export function resetStage() {
  stage.spot = null
  stage.caption = null
  stage.cursor.down = false
  document.querySelector<HTMLElement>('[role="dialog"] button.btn-icon')?.click()
}

export function cancelRuns() {
  generation++
  abort.abort()
}

function live(run: number) {
  if (run !== generation) {
    throw new Skipped()
  }
}

export interface Director {
  wait(ms: number): Promise<void>
  go(path: string): Promise<void>
  find(selector: string, text?: string): Promise<HTMLElement>
  scrollTo(target: string | number, ms?: number): Promise<void>
  point(target: Target): Promise<HTMLElement>
  click(target: Target): Promise<void>
  key(key: string): void
  type(selector: string, text: string): Promise<void>
  say(title: string, body?: string, at?: 'top' | 'bottom'): void
  hush(): void
  spot(selector: string | null): Promise<void>
  scene(component: Component | null): void
  cursor(visible: boolean): void
  settle(): Promise<void>
}

type Target = string | HTMLElement

const ease = (t: number) => (t < 0.5 ? 4 * t * t * t : 1 - (-2 * t + 2) ** 3 / 2)

export function director(run: number): Director {
  const signal = abort.signal
  const wait = (ms: number) =>
    new Promise<void>((resolve) => {
      const timer = setTimeout(resolve, ms * stage.pace)
      signal.addEventListener('abort', () => (clearTimeout(timer), resolve()), { once: true })
    }).then(() => live(run))

  async function settle() {
    let quiet = 0
    while (quiet < 3) {
      await new Promise((resolve) => requestAnimationFrame(resolve))
      quiet = pendingRequests() === 0 ? quiet + 1 : 0
    }
    await nextTick()
    live(run)
  }

  async function find(selector: string, text?: string) {
    const started = performance.now()
    for (;;) {
      const el = [...document.querySelectorAll<HTMLElement>(selector)].find(
        (candidate) =>
          candidate.getClientRects().length && (!text || candidate.textContent?.includes(text)),
      )
      if (el) {
        return el
      }
      if (performance.now() - started > 6000) {
        throw new Error(`show: nothing matches ${selector} ${text ?? ''}`)
      }
      await new Promise((resolve) => setTimeout(resolve, 50))
      live(run)
    }
  }

  async function tweenScroll(top: number, ms: number) {
    const from = scrollY
    const to = Math.max(0, Math.min(top, document.documentElement.scrollHeight - innerHeight))
    const duration = ms * stage.pace
    if (Math.abs(to - from) < 2 || duration < 16) {
      scrollTo({ top: to, behavior: 'instant' })
      return
    }
    const start = performance.now()
    await new Promise<void>((resolve) => {
      const frame = (now: number) => {
        const t = Math.min(1, (now - start) / duration)
        scrollTo({ top: from + (to - from) * ease(t), behavior: 'instant' })
        if (t < 1 && run === generation) requestAnimationFrame(frame)
        else resolve()
      }
      requestAnimationFrame(frame)
    })
    live(run)
  }

  async function scrollTo_(target: string | number, ms = 1100) {
    if (typeof target === 'number') {
      return tweenScroll(target, ms)
    }
    const el = await find(target)
    const rect = el.getBoundingClientRect()
    return tweenScroll(rect.top + scrollY - HEADER - 24, ms)
  }

  async function reveal(el: HTMLElement) {
    if (el.closest('[role="dialog"]')) {
      el.scrollIntoView({ block: 'nearest', behavior: 'instant' })
      return
    }
    const rect = el.getBoundingClientRect()
    if (rect.top < HEADER + 16 || rect.bottom > innerHeight - 32) {
      await tweenScroll(rect.top + scrollY - innerHeight / 3, 800)
    }
  }

  async function point(target: Target) {
    const el = typeof target === 'string' ? await find(target) : target
    await reveal(el)
    const rect = el.getBoundingClientRect()
    const x = rect.left + Math.min(rect.width / 2, 48)
    const y = rect.top + rect.height / 2
    const distance = Math.hypot(x - stage.cursor.x, y - stage.cursor.y)
    const ms = Math.min(950, Math.max(380, distance * 0.9))
    stage.cursor.visible = true
    stage.cursor.ms = ms * stage.pace
    stage.cursor.x = x
    stage.cursor.y = y
    await wait(ms + 60)
    return el
  }

  async function click(target: Target) {
    const el = await point(target)
    stage.cursor.down = true
    await wait(140)
    stage.cursor.down = false
    el.click()
    await settle()
    await wait(220)
  }

  async function type(selector: string, text: string) {
    await click(selector)
    const el = (await find(selector)) as HTMLInputElement
    el.focus()
    const setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(el), 'value')!.set!
    for (const char of text) {
      setter.call(el, el.value + char)
      el.dispatchEvent(new Event('input', { bubbles: true }))
      await wait(70 + Math.random() * 50)
    }
    await settle()
  }

  return {
    wait,
    settle,
    find,
    point,
    click,
    type,
    scrollTo: scrollTo_,
    key(key) {
      const target =
        document.activeElement instanceof HTMLElement ? document.activeElement : document.body
      target.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }))
    },
    async go(path) {
      stage.spot = null
      if (router.currentRoute.value.fullPath !== path) {
        await router.push(path)
      }
      await settle()
      scrollTo({ top: 0, behavior: 'instant' })
      await wait(250)
    },
    say(title, body, at = 'bottom') {
      stage.caption = { title, body, at }
    },
    hush() {
      stage.caption = null
    },
    async spot(selector) {
      stage.spot = selector ? await find(selector) : null
    },
    scene(component) {
      stage.sceneTake++
      stage.scene = component
    },
    cursor(visible) {
      stage.cursor.visible = visible
    },
  }
}
