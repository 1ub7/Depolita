import clsx from 'clsx'
import { formatBytes } from '@/shared/lib/format'
import { Card } from '@/shared/ui/Card'
import { useAppStats } from '../queries'

/** 서버 DockerContainerRuntime.NANO_CPUS(0.5 CPU) 와 같은 값. CPU 막대를 이 값 기준으로 채운다 */
const CPU_LIMIT_PERCENT = 50

function Meter({ label, value, ratio }: { label: string; value: string; ratio: number }) {
  const percent = Math.min(100, Math.max(0, ratio * 100))
  return (
    <div className="flex flex-col gap-2 px-6 py-4">
      <div className="flex items-baseline justify-between gap-3">
        <span className="text-muted text-xs">{label}</span>
        <span className="font-medium tabular-nums">{value}</span>
      </div>
      <div
        className="bg-surface-2 h-1.5 overflow-hidden rounded-full"
        role="meter"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.round(percent)}
      >
        <div
          className={clsx(
            'h-full rounded-full transition-[width] duration-500',
            percent >= 90 ? 'bg-danger' : percent >= 70 ? 'bg-warn' : 'bg-accent',
          )}
          style={{ width: `${percent}%` }}
        />
      </div>
    </div>
  )
}

export function ResourceUsage({ appId }: { appId: number }) {
  const { data: stats, error } = useAppStats(appId, true)

  return (
    <Card className="py-1">
      {stats ? (
        <div className="grid grid-cols-1 sm:grid-cols-2">
          <Meter
            label={`CPU (제한 ${CPU_LIMIT_PERCENT}%)`}
            value={`${stats.cpuPercent.toFixed(1)}%`}
            ratio={stats.cpuPercent / CPU_LIMIT_PERCENT}
          />
          <Meter
            label="메모리"
            value={`${formatBytes(stats.memoryUsageBytes)} / ${formatBytes(stats.memoryLimitBytes)}`}
            ratio={stats.memoryLimitBytes > 0 ? stats.memoryUsageBytes / stats.memoryLimitBytes : 0}
          />
        </div>
      ) : (
        <p className="text-muted px-6 py-4 text-xs">
          {error ? '리소스 사용량을 가져오지 못했습니다.' : '리소스 사용량을 불러오는 중…'}
        </p>
      )}
    </Card>
  )
}
