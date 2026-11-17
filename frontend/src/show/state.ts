import { ref } from 'vue'

const ROOM_KEY = 'show.room'
const LANG_KEY = 'show.lang'
export type ShowLang = 'en' | 'ro'
export const ROOM_PATTERN = /^[a-z0-9-]{4,40}$/

function storedRoom(): string | null {
  try {
    return sessionStorage.getItem(ROOM_KEY)
  } catch {
    return null
  }
}

export const showRoom = ref<string | null>(storedRoom())
export const showMirror = ref(false)

export function showLang(): ShowLang {
  try {
    return sessionStorage.getItem(LANG_KEY) === 'ro' ? 'ro' : 'en'
  } catch {
    return 'en'
  }
}

export function startShow(room: string, lang: ShowLang, mirror = false) {
  showRoom.value = room
  showMirror.value = mirror
  // the mirror lives in an iframe that shares sessionStorage with the remote page
  if (mirror) return
  try {
    sessionStorage.setItem(LANG_KEY, lang)
    sessionStorage.setItem(ROOM_KEY, room)
  } catch {
    // storage blocked: the show lasts until the tab reloads
  }
}

export function stopShow() {
  showRoom.value = null
  try {
    sessionStorage.removeItem(ROOM_KEY)
  } catch {
    // nothing stored
  }
}

export interface StageState {
  from: 'stage'
  beat: number
  playing: boolean
  ready: boolean
  lang: ShowLang
}

export interface RemoteCommand {
  from: 'remote'
  id: string
  at: number
  cmd: 'next' | 'prev' | 'replay' | 'goto' | 'lang'
  beat?: number
  lang?: ShowLang
}

export type ShowMessage = StageState | RemoteCommand

export function showToken(offeringId: number) {
  return { offeringId, fingerprint: 'show', sig: '', submitted: false, msg: 'show' }
}
