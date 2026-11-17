import { api } from '@/api'
import type { ShowMessage } from './state'

export function listen(
  room: string,
  onMessage: (message: ShowMessage) => void,
  onLink: (up: boolean) => void,
) {
  const source = new EventSource(`/api/show/${room}/events`)
  source.onopen = () => onLink(true)
  source.onerror = () => onLink(false)
  source.onmessage = (event) => {
    try {
      onMessage(JSON.parse(event.data) as ShowMessage)
    } catch {
      // not a show message
    }
  }
  return () => source.close()
}

export function send(room: string, message: ShowMessage) {
  return api('POST', `/api/show/${room}`, message)
}
