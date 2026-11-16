export interface DateRange {
  from: string
  to: string
}

const RANGE = /([[(])(\d{4}-\d{2}-\d{2})?,(\d{4}-\d{2}-\d{2})?([\])])/g

function previousDay(date: string) {
  const day = new Date(`${date}T00:00:00Z`)
  day.setUTCDate(day.getUTCDate() - 1)
  return day.toISOString().slice(0, 10)
}

// Postgres returns ranges as [first,dayAfterLast); the form shows the last day itself.
export function parseRanges(text: string | null | undefined): DateRange[] {
  return [...(text ?? '').matchAll(RANGE)].map(([, , from = '', to = '', close]) => ({
    from,
    to: to && close === ')' ? previousDay(to) : to,
  }))
}

export function formatRange({ from, to }: DateRange) {
  return from || to ? `[${from},${to}]` : ''
}

export function formatRanges(ranges: DateRange[]) {
  const filled = ranges.map(formatRange).filter(Boolean)
  return filled.length ? `{${filled.join(',')}}` : ''
}
