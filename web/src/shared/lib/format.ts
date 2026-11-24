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

export function formatDuration(ms: number): string {
  if (ms < 1_000) return `${ms}ms`
  // 반올림을 먼저 하고 단위를 고른다. 그래야 59.99초가 "60.0초", 119.6초가 "1분 60초"로 나오지 않는다
  const tenths = Math.round(ms / 100) / 10
  if (tenths < 60) return `${tenths.toFixed(1)}초`
  const totalSec = Math.round(ms / 1_000)
  const min = Math.floor(totalSec / 60)
  const sec = totalSec % 60
  return sec === 0 ? `${min}분` : `${min}분 ${sec}초`
}
