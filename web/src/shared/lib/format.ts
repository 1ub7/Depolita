const rtf = new Intl.RelativeTimeFormat('ko', { numeric: 'auto' })

const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['day', 86_400],
  ['hour', 3_600],
  ['minute', 60],
]

export function parseServerDate(value: string): Date {
  return new Date(value)
}

export function timeAgo(value: string, now = Date.now()): string {
  const diffSec = Math.round((parseServerDate(value).getTime() - now) / 1000)
  for (const [unit, sec] of UNITS) {
    if (Math.abs(diffSec) >= sec) return rtf.format(Math.round(diffSec / sec), unit)
  }
  return '방금'
}

const dateTimeFormat = new Intl.DateTimeFormat('ko', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

export function formatDateTime(value: string): string {
  return dateTimeFormat.format(parseServerDate(value))
}

export function shortRepo(url: string): string {
  return url.replace(/^https?:\/\//, '').replace(/\.git$/, '')
}