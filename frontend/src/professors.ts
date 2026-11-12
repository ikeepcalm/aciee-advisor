import type { Option } from '@/api'

export function pillarAverages(axes: Option[], pillars: Option[], scores: (number | null)[]) {
  return pillars.map((pillar) => {
    const values = axes
      .map((axis, i) => (axis.group === pillar.code ? scores[i] : null))
      .filter((v): v is number => v != null)
    return {
      label: pillar.label,
      value: values.length ? values.reduce((a, b) => a + b, 0) / values.length : null,
    }
  })
}

export function academicYear(week: string): string {
  const year = Number(week.slice(0, 4))
  const start = Number(week.slice(5, 7)) >= 10 ? year : year - 1
  return `${start}–${start + 1}`
}
