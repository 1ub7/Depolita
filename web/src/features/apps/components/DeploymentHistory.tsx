import clsx from 'clsx'
import { formatDateTime, formatDuration, shortRepo } from '@/shared/lib/format'
import { Card } from '@/shared/ui/Card'
import type { Deployment, DeploymentStatus, DeployTrigger } from '../model/types'
import { useDeployments } from '../queries'

const TRIGGER_LABEL: Record<DeployTrigger, string> = {
  INITIAL: '최초 배포',
  MANUAL: '수동 재배포',
  WEBHOOK: 'GitHub push',
}

const STATUS_META: Record<DeploymentStatus, { label: string; className: string }> = {
  IN_PROGRESS: { label: '진행 중', className: 'text-warn' },
  SUCCEEDED: { label: '성공', className: 'text-accent' },
  FAILED: { label: '실패', className: 'text-danger' },
}

const GRID = 'grid grid-cols-[150px_110px_100px_90px_90px_minmax(0,1fr)] items-center gap-4'

type Props = {
  appId: number
  repositoryUrl: string
}

/** 시작부터 끝까지 걸린 시간. 진행 중이면 null */
function totalMs(d: Deployment): number | null {
  if (!d.finishedAt) return null
  return new Date(d.finishedAt).getTime() - new Date(d.startedAt).getTime()
}

function commitUrl(repositoryUrl: string, sha: string) {
  return `https://${shortRepo(repositoryUrl)}/commit/${sha}`
}

export function DeploymentHistory({ appId, repositoryUrl }: Props) {
  const { data: deployments = [], isPending, error } = useDeployments(appId)

  return (
    <Card className="overflow-hidden">
      <div className="border-line border-b px-6 py-4">
        <h2 className="text-[15px] font-semibold">
          배포 이력 <span className="text-muted font-normal">최근 {deployments.length}건</span>
        </h2>
      </div>

      {isPending ? (
        <div className="h-24 animate-pulse" aria-label="불러오는 중" />
      ) : error ? (
        <p className="text-danger-soft px-6 py-8 text-center">{error.message}</p>
      ) : deployments.length === 0 ? (
        <p className="text-muted px-6 py-8 text-center">아직 배포 이력이 없습니다.</p>
      ) : (
        <div className="overflow-x-auto">
          <div className="min-w-[760px]" role="table">
            <div
              role="row"
              className={clsx(GRID, 'text-muted border-line border-b px-6 py-2.5 text-xs')}
            >
              <span role="columnheader">시작</span>
              <span role="columnheader">트리거</span>
              <span role="columnheader">커밋</span>
              <span role="columnheader">빌드</span>
              <span role="columnheader">전체</span>
              <span role="columnheader">결과</span>
            </div>
            {deployments.map((d) => {
              const total = totalMs(d)
              const status = STATUS_META[d.status]
              return (
                <div
                  key={d.id}
                  role="row"
                  className={clsx(GRID, 'border-line border-b px-6 py-3 last:border-b-0')}
                >
                  <span role="cell" className="text-fg-soft">
                    {formatDateTime(d.startedAt)}
                  </span>
                  <span role="cell">{TRIGGER_LABEL[d.trigger]}</span>
                  <span role="cell" className="font-mono text-xs">
                    {d.commitSha ? (
                      <a
                        href={commitUrl(repositoryUrl, d.commitSha)}
                        target="_blank"
                        rel="noreferrer"
                        className="text-accent hover:underline"
                      >
                        {d.commitSha.slice(0, 7)}
                      </a>
                    ) : (
                      <span className="text-faint">—</span>
                    )}
                  </span>
                  <span role="cell" className="text-fg-soft">
                    {d.buildMs != null ? formatDuration(d.buildMs) : '—'}
                  </span>
                  <span role="cell" className="text-fg-soft">
                    {total != null ? formatDuration(total) : '—'}
                  </span>
                  <span role="cell" className="min-w-0">
                    <span className={clsx('font-medium', status.className)}>{status.label}</span>
                    {d.failureReason && (
                      <span
                        className="text-muted ml-2 block truncate text-xs sm:inline"
                        title={d.failureReason}
                      >
                        {d.failureReason}
                      </span>
                    )}
                  </span>
                </div>
              )
            })}
          </div>
        </div>
      )}
    </Card>
  )
}
