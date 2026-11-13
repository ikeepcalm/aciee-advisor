export const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)')
let running: { transition: ViewTransition; kind: string } | null = null

export function animateSwap(kind: string, update: () => void | Promise<void>) {
  const root = document.documentElement
  if (!('startViewTransition' in document) || reducedMotion.matches) {
    Promise.resolve(update()).catch(() => {})
    return
  }
  if (running) root.classList.remove(running.kind)
  const transition = document.startViewTransition(update)
  running = { transition, kind }
  root.classList.add(kind)
  transition.finished
    .catch(() => {})
    .finally(() => {
      if (running?.transition !== transition) return
      root.classList.remove(kind)
      running = null
    })
}
