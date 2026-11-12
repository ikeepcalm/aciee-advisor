export function academicYear(week: string): string {
  const year = Number(week.slice(0, 4))
  const start = Number(week.slice(5, 7)) >= 10 ? year : year - 1
  return `${start}–${start + 1}`
}
