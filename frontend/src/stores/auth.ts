import { ref } from 'vue'
import { defineStore } from 'pinia'
import { api } from '@/api'
import { rateDraftPrefix, removeStoredStartingWith } from '@/storage'

export interface Account {
  id: number
  email: string
  displayName: string | null
  roles: string[]
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<Account | null>(null)
  const pendingPassword = ref('')
  const loaded = ref(false)
  let loading: Promise<void> | null = null

  function load() {
    loading ??= api<Account>('GET', '/api/auth/me')
      .then(
        (account) => void (user.value = account),
        () => void (user.value = null),
      )
      .finally(() => (loaded.value = true))
    return loading
  }

  async function signUp(email: string, password: string) {
    pendingPassword.value = password
    await api('POST', '/api/auth/signup', { email, password })
  }

  async function signIn(email: string, password: string) {
    pendingPassword.value = password
    user.value = await api<Account>('POST', '/api/auth/signin', { email, password })
    pendingPassword.value = ''
  }

  async function verify(email: string, code: string, password: string) {
    user.value = await api<Account>('POST', '/api/auth/verify', { email, code, password })
    pendingPassword.value = ''
  }

  async function rename(name: string) {
    user.value = await api<Account>('PUT', '/api/auth/me/name', { name })
  }

  async function signOut() {
    await api('POST', '/api/auth/signout')
    removeStoredStartingWith(rateDraftPrefix)
    user.value = null
  }

  return { user, pendingPassword, loaded, load, signUp, signIn, verify, rename, signOut }
})
