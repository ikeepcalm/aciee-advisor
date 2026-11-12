export function readStored(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

export function writeStored(key: string, value: string) {
  try {
    localStorage.setItem(key, value)
  } catch {
    // storage blocked: the choice lasts until the tab closes
  }
}

export const rateDraftPrefix = 'rate-draft:'

export function removeStoredStartingWith(prefix: string) {
  try {
    Object.keys(localStorage)
      .filter((key) => key.startsWith(prefix))
      .forEach((key) => localStorage.removeItem(key))
  } catch {
    return
  }
}

export function removeStored(key: string) {
  try {
    localStorage.removeItem(key)
  } catch {
    return
  }
}
