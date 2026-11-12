import { BUBBLE, BUBBLE_LETTER } from '@/brand'

interface WeekImageLesson {
  start: string
  end: string
  title: string
  kind: string | null
  kindLabel: string
  room: string | null
  teacher: string | null
  tag: string | null
}

interface WeekImageDay {
  name: string
  date: string
  lessons: WeekImageLesson[]
}

export interface WeekImage {
  group: string
  program: string
  range: string
  parity: { label: string; odd: boolean } | null
  days: WeekImageDay[]
  free: string
  updated: string | null
}

const WIDTH = 1080
const MIN_HEIGHT = 1350
const MAX_HEIGHT = 1920
const MARGIN = 56
const HEADER = 286
const FOOTER = 132
const DAY_COLUMN = 168
const ROW = 86
const FREE_ROW = 64
const DAY_GAP = 18
const MAX_SCALE = 1.2
const BRAND = 'ACIEE Advisor'
const SITE = 'acieeadvisor.ro'
const DISPLAY = "'Caveat', 'IBM Plex Sans', sans-serif"
const SANS = "'IBM Plex Sans', system-ui, sans-serif"
const MONO = "'Martian Mono', ui-monospace, monospace"
const PAPER = {
  board: '#f7f3e6',
  panel: '#fffdf5',
  ink: '#20263d',
  muted: '#5e6272',
  line: '#9d9785',
  rule: 'rgb(70 110 180 / 0.13)',
  mask: '#2946b3',
  copper: '#c13a2c',
  mark: '#ffe270',
}
const KIND: Record<string, string> = {
  C: '#ad5008',
  S: '#1f7a4d',
  L: '#6a47c2',
  P: '#86680c',
  X: '#5e6272',
}

function fit(ctx: CanvasRenderingContext2D, text: string, max: number): string {
  if (ctx.measureText(text).width <= max) {
    return text
  }
  let cut = text
  while (cut.length > 1 && ctx.measureText(`${cut}…`).width > max) {
    cut = cut.slice(0, -1)
  }
  return `${cut.trimEnd()}…`
}

function dayHeight(day: WeekImageDay): number {
  return day.lessons.length ? day.lessons.length * ROW : FREE_ROW
}

async function loadFonts(text: string) {
  const fonts = [
    `700 40px ${DISPLAY}`,
    `400 24px ${SANS}`,
    `600 24px ${SANS}`,
    `400 24px ${MONO}`,
    `500 24px ${MONO}`,
  ]
  await Promise.all(fonts.map((font) => document.fonts.load(font, text)))
  await document.fonts.ready
}

function drawPaper(ctx: CanvasRenderingContext2D, height: number) {
  ctx.fillStyle = PAPER.board
  ctx.fillRect(0, 0, WIDTH, height)
  ctx.strokeStyle = PAPER.rule
  ctx.lineWidth = 2
  ctx.beginPath()
  for (let x = 36; x < WIDTH; x += 36) {
    ctx.moveTo(x, 0)
    ctx.lineTo(x, height)
  }
  for (let y = 36; y < height; y += 36) {
    ctx.moveTo(0, y)
    ctx.lineTo(WIDTH, y)
  }
  ctx.stroke()
}

function drawBubble(ctx: CanvasRenderingContext2D, x: number, y: number, size: number) {
  ctx.save()
  ctx.translate(x, y)
  ctx.scale(size / 32, size / 32)
  ctx.lineJoin = 'round'
  ctx.lineCap = 'round'
  ctx.fillStyle = PAPER.mark
  ctx.strokeStyle = PAPER.ink
  ctx.lineWidth = 2
  const bubble = new Path2D(BUBBLE)
  ctx.fill(bubble)
  ctx.stroke(bubble)
  ctx.lineWidth = 2.2
  ctx.stroke(new Path2D(BUBBLE_LETTER))
  ctx.restore()
}

function drawHeader(ctx: CanvasRenderingContext2D, week: WeekImage) {
  ctx.textBaseline = 'alphabetic'
  ctx.font = `700 124px ${DISPLAY}`
  const groupWidth = ctx.measureText(week.group).width
  ctx.save()
  ctx.translate(MARGIN - 14, 86)
  ctx.rotate(-0.012)
  ctx.fillStyle = PAPER.mark
  ctx.beginPath()
  ctx.roundRect(0, 0, groupWidth + 36, 104, [44, 14, 40, 16])
  ctx.fill()
  ctx.restore()
  ctx.fillStyle = PAPER.ink
  ctx.fillText(week.group, MARGIN, 172)

  ctx.font = `400 28px ${SANS}`
  ctx.fillStyle = PAPER.muted
  ctx.fillText(fit(ctx, week.program, WIDTH - MARGIN * 2), MARGIN, 236)

  ctx.textAlign = 'right'
  ctx.font = `700 68px ${DISPLAY}`
  ctx.fillStyle = PAPER.ink
  ctx.fillText(week.range, WIDTH - MARGIN, 136)
  if (week.parity) {
    const color = week.parity.odd ? PAPER.copper : PAPER.mask
    const label = week.parity.label.toUpperCase()
    ctx.font = `500 21px ${MONO}`
    ctx.letterSpacing = '1.5px'
    const width = ctx.measureText(label).width + 44
    ctx.strokeStyle = color
    ctx.lineWidth = 2.5
    ctx.fillStyle = PAPER.panel
    ctx.beginPath()
    ctx.roundRect(WIDTH - MARGIN - width, 160, width, 46, 23)
    ctx.fill()
    ctx.stroke()
    ctx.fillStyle = color
    ctx.fillText(label, WIDTH - MARGIN - 21, 191)
    ctx.letterSpacing = '0px'
  }
  ctx.textAlign = 'left'
}

function drawLesson(
  ctx: CanvasRenderingContext2D,
  lesson: WeekImageLesson,
  x: number,
  y: number,
  width: number,
  s: number,
) {
  const color = KIND[lesson.kind ?? 'X'] ?? KIND.X!
  const row = ROW * s
  ctx.fillStyle = color
  ctx.beginPath()
  ctx.roundRect(x + 128 * s, y + 13 * s, 6 * s, row - 26 * s, 3 * s)
  ctx.fill()

  ctx.font = `500 ${24 * s}px ${MONO}`
  ctx.fillStyle = PAPER.ink
  ctx.fillText(lesson.start, x + 22 * s, y + 39 * s)
  ctx.font = `400 ${19 * s}px ${MONO}`
  ctx.fillStyle = PAPER.muted
  ctx.fillText(lesson.end, x + 22 * s, y + 66 * s)

  const textX = x + 152 * s
  let textWidth = width - 152 * s - 22 * s
  if (lesson.tag) {
    ctx.font = `500 ${17 * s}px ${MONO}`
    const tag = lesson.tag.toUpperCase()
    const tagWidth = ctx.measureText(tag).width
    ctx.fillStyle = PAPER.muted
    ctx.fillText(tag, x + width - 22 * s - tagWidth, y + 66 * s)
    textWidth -= tagWidth + 16 * s
  }
  ctx.font = `600 ${28 * s}px ${SANS}`
  ctx.fillStyle = PAPER.ink
  ctx.fillText(fit(ctx, lesson.title, width - 152 * s - 22 * s), textX, y + 38 * s)

  ctx.font = `500 ${17 * s}px ${MONO}`
  ctx.fillStyle = color
  const kind = lesson.kindLabel.toUpperCase()
  ctx.fillText(kind, textX, y + 66 * s)
  let cursor = textX + ctx.measureText(kind).width + 16 * s
  if (lesson.room) {
    ctx.font = `500 ${20 * s}px ${MONO}`
    ctx.fillStyle = PAPER.ink
    ctx.fillText(lesson.room, cursor, y + 66 * s)
    cursor += ctx.measureText(lesson.room).width + 16 * s
  }
  if (lesson.teacher) {
    ctx.font = `400 ${22 * s}px ${SANS}`
    ctx.fillStyle = PAPER.muted
    ctx.fillText(fit(ctx, lesson.teacher, textX + textWidth - cursor), cursor, y + 66 * s)
  }
}

function drawDay(
  ctx: CanvasRenderingContext2D,
  day: WeekImageDay,
  y: number,
  s: number,
  free: string,
) {
  const height = dayHeight(day) * s
  const x = MARGIN + DAY_COLUMN
  const width = WIDTH - MARGIN - x
  ctx.font = `700 ${50 * s}px ${DISPLAY}`
  const shrink = Math.min(1, (DAY_COLUMN - 14) / ctx.measureText(day.name).width)
  ctx.font = `700 ${50 * s * shrink}px ${DISPLAY}`
  ctx.fillStyle = PAPER.ink
  ctx.fillText(day.name, MARGIN, y + 44 * s)
  if (day.lessons.length) {
    ctx.font = `400 ${18 * s}px ${MONO}`
    ctx.fillStyle = PAPER.muted
    ctx.fillText(day.date, MARGIN + 2, y + 78 * s)
  }

  ctx.beginPath()
  ctx.roundRect(x, y, width, height, [30, 12, 26, 14])
  ctx.fillStyle = PAPER.panel
  ctx.fill()
  ctx.strokeStyle = PAPER.line
  ctx.lineWidth = 2
  ctx.setLineDash(day.lessons.length ? [] : [10, 8])
  ctx.stroke()
  ctx.setLineDash([])

  if (!day.lessons.length) {
    ctx.font = `400 ${24 * s}px ${SANS}`
    ctx.fillStyle = PAPER.muted
    ctx.fillText(free, x + 22 * s, y + height / 2 + 8 * s)
    return
  }
  day.lessons.forEach((lesson, index) => {
    const top = y + index * ROW * s
    if (index) {
      ctx.strokeStyle = PAPER.line
      ctx.lineWidth = 1.5
      ctx.setLineDash([6, 7])
      ctx.beginPath()
      ctx.moveTo(x + 18, top)
      ctx.lineTo(x + width - 18, top)
      ctx.stroke()
      ctx.setLineDash([])
    }
    drawLesson(ctx, lesson, x, top, width, s)
  })
}

function drawFooter(ctx: CanvasRenderingContext2D, height: number, updated: string | null) {
  const baseline = height - 54
  ctx.strokeStyle = PAPER.line
  ctx.lineWidth = 2
  ctx.setLineDash([10, 8])
  ctx.beginPath()
  ctx.moveTo(MARGIN, height - FOOTER + 22)
  ctx.lineTo(WIDTH - MARGIN, height - FOOTER + 22)
  ctx.stroke()
  ctx.setLineDash([])
  drawBubble(ctx, MARGIN, baseline - 44, 60)
  ctx.font = `700 44px ${DISPLAY}`
  ctx.fillStyle = PAPER.ink
  ctx.fillText(BRAND, MARGIN + 76, baseline)
  const brandWidth = ctx.measureText(BRAND).width
  ctx.font = `400 20px ${MONO}`
  ctx.fillStyle = PAPER.mask
  ctx.fillText(SITE, MARGIN + 76 + brandWidth + 18, baseline - 3)
  if (updated) {
    ctx.textAlign = 'right'
    ctx.font = `400 22px ${SANS}`
    ctx.fillStyle = PAPER.muted
    ctx.fillText(updated, WIDTH - MARGIN, baseline - 3)
    ctx.textAlign = 'left'
  }
}

export async function drawWeekImage(week: WeekImage): Promise<Blob> {
  await loadFonts(JSON.stringify(week) + BRAND + SITE)
  const gaps = (week.days.length - 1) * DAY_GAP
  const natural = week.days.reduce((sum, day) => sum + dayHeight(day), 0)
  const height = Math.min(MAX_HEIGHT, Math.max(MIN_HEIGHT, HEADER + natural + gaps + FOOTER))
  const scale = Math.min(MAX_SCALE, (height - HEADER - FOOTER - gaps) / natural)
  const canvas = document.createElement('canvas')
  canvas.width = WIDTH
  canvas.height = height
  const ctx = canvas.getContext('2d')
  if (!ctx) {
    throw new Error('canvas unavailable')
  }
  drawPaper(ctx, height)
  drawHeader(ctx, week)
  const used = natural * scale + gaps
  let y = HEADER + (height - HEADER - FOOTER - used) / 2
  for (const day of week.days) {
    drawDay(ctx, day, y, scale, week.free)
    y += dayHeight(day) * scale + DAY_GAP
  }
  drawFooter(ctx, height, week.updated)
  return new Promise((resolve, reject) =>
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('canvas export failed'))),
      'image/png',
    ),
  )
}
