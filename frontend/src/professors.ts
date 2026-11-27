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

// with no class type (a professor's overall radar) it keeps whatever criteria were scored
export function radarFor(
  axes: Option[],
  pillars: Option[],
  scores: (number | null)[],
  component?: string | null,
) {
  const keeps = (group: string | null, index: number) =>
    component ? group === component || group === 'grading' : scores[index] != null
  const kept = axes.map((axis, index) => keeps(axis.group, index))
  return {
    axes: axes.filter((_, index) => kept[index]),
    scores: scores.filter((_, index) => kept[index]),
    pillars: pillars.filter((pillar) => !component || keeps(pillar.code, -1)),
  }
}

export function academicYear(week: string): string {
  const year = Number(week.slice(0, 4))
  const start = Number(week.slice(5, 7)) >= 10 ? year : year - 1
  return `${start}–${start + 1}`
}
