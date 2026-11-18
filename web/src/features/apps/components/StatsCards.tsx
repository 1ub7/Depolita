import clsx from 'clsx'
import { isInProgress } from '../model/status'
import type { App } from '../model/types'

export function StatsCards({ apps }: { apps: App[] }) {
  const count = (predicate: (app: App) => boolean) => apps.filter(predicate).length
  const stats = [
    { label: '실행 중', value: count((a) => a.status === 'RUNNING'), dot: 'bg-accent' },
    { label: '진행 중', value: count((a) => isInProgress(a.status)), dot: 'bg-warn' },
    { label: '실패', value: count((a) => a.status === 'FAILED'), dot: 'bg-danger' },
    { label: '중지됨', value: count((a) => a.status === 'STOPPED'), dot: 'bg-idle' },
  ]

  return (
    <section className="grid grid-cols-2 gap-4 md:grid-cols-4" aria-label="상태별 앱 수">
      {stats.map((s) => (
        <div
          key={s.label}
          className="border-line bg-surface flex flex-col gap-1.5 rounded-[14px] border px-5 py-[18px]"
        >
          <span className="text-muted flex items-center gap-2 text-[13px]">
            <span className={clsx('size-2 rounded-full', s.dot)} />
            {s.label}
          </span>
          <span className="text-[26px] font-semibold tracking-[-0.02em] tabular-nums">
            {s.value}
          </span>
        </div>
      ))}
    </section>
  )
}
