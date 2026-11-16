// Two letters for the avatar: first and last word of the name, else the start of the address.
export function initials(name: string | null | undefined, email: string) {
  const words = (name ?? '').match(/\p{L}+/gu) ?? []
  const letters =
    words.length > 1
      ? words[0]![0]! + words.at(-1)![0]!
      : (words[0] ?? email.split('@')[0]!.replace(/[^\p{L}\d]/gu, '')).slice(0, 2)
  return letters.toLocaleUpperCase()
}

export function shortName(name: string | null | undefined, email: string) {
  return name || email.split('@')[0]!
}
