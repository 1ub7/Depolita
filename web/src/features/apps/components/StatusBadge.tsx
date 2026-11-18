import clsx from 'clsx'
import { STATUS_META, TONE_CLASS, isInProgress } from '../model/status'
import type { AppStatus } from '../model/types'

export function StatusBadge({ status }: { status: AppStatus }) {
  const { label, tone } = STATUS_META[status]
  const toneClass = TONE_CLASS[tone]
  return (
    <span
      className={clsx(
        'inline-flex items-center gap-[7px] rounded-full px-[11px] py-1 text-xs font-medium whitespace-nowrap',
        toneClass.text,
        toneClass.bg,
      )}
    >
      <span
        className={clsx(
          'size-1.5 rounded-full',
          toneClass.dot,
          isInProgress(status) && 'animate-pulse',
        )}
      />
      {label}
    </span>
  )
}
