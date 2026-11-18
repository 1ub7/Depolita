import clsx from 'clsx'
import { Link } from 'react-router'
import { shortRepo, timeAgo } from '@/shared/lib/format'
import { Card } from '@/shared/ui/Card'
import type { App } from '../model/types'
import { AppActions } from './AppActions'
import { StatusBadge } from './StatusBadge'

const GRID = 'grid grid-cols-[200px_120px_140px_minmax(0,1fr)_90px_200px] items-center gap-4'

type Props = {
  apps: App[]
  isFetching: boolean
}

export function AppTable({ apps, isFetching }: Props) {
  return (
    <Card className="overflow-hidden">
      <div className="border-line flex flex-wrap items-center justify-between gap-3 border-b px-6 py-[18px]">
        <h2 className="text-base font-semibold">
          배포된 앱 <span className="text-muted font-normal">{apps.length}</span>
        </h2>
        <span className="text-muted flex items-center gap-2 text-xs">
          <span
            className={clsx(
              'bg-muted size-1.5 rounded-full',
              isFetching && 'bg-accent animate-pulse',
            )}
          />
          자동 갱신 중
        </span>
      </div>

      {apps.length === 0 ? (
        <p className="text-muted px-6 py-14 text-center">
          아직 배포한 앱이 없습니다. 위에서 첫 배포를 시작해 보세요.
        </p>
      ) : (
        <div className="overflow-x-auto">
          <div className="min-w-[960px]" role="table">
            <div
              role="row"
              className={clsx(GRID, 'bg-surface-sunken text-muted px-6 py-3 text-xs')}
            >
              <span role="columnheader">이름</span>
              <span role="columnheader">상태</span>
              <span role="columnheader">포트</span>
              <span role="columnheader">접속 주소</span>
              <span role="columnheader">업데이트</span>
              <span role="columnheader" className="text-right">
                작업
              </span>
            </div>
            {apps.map((app) => (
              <AppRow key={app.id} app={app} />
            ))}
          </div>
        </div>
      )}
    </Card>
  )
}

function AppRow({ app }: { app: App }) {
  return (
    <div role="row" className="border-t border-[#1a1f1d]">
      <div className={clsx(GRID, 'px-6 py-4')}>
        <div role="cell" className="flex min-w-0 flex-col gap-0.5">
          <Link to={`/apps/${app.id}`} className="hover:text-accent truncate font-semibold">
            {app.name}
          </Link>
          <span className="text-muted truncate text-xs">{shortRepo(app.repositoryUrl)}</span>
        </div>
        <span role="cell">
          <StatusBadge status={app.status} />
        </span>
        <span role="cell" className="text-fg-soft font-mono text-[13px]">
          {app.internalPort} → {app.assignedPort ?? '—'}
        </span>
        <span role="cell" className="truncate text-[13px]">
          {app.url ? (
            <a
              href={app.url}
              target="_blank"
              rel="noreferrer"
              className="text-accent hover:underline"
            >
              {app.url}
            </a>
          ) : (
            <span className="text-[#5e6662]">—</span>
          )}
        </span>
        <span role="cell" className="text-muted text-[13px]">
          {timeAgo(app.updatedAt)}
        </span>
        <div role="cell" className="flex justify-end gap-1.5">
          <Link
            to={`/apps/${app.id}`}
            className="bg-surface-2 text-fg rounded-lg px-3 py-1.5 text-xs font-medium hover:bg-[#222826]"
          >
            로그
          </Link>
          <AppActions app={app} />
        </div>
      </div>
      {app.status === 'FAILED' && app.failureReason && (
        <p className="bg-danger/8 text-danger-soft mx-6 mb-4 rounded-[10px] px-3.5 py-2.5 text-xs">
          {app.failureReason}
        </p>
      )}
    </div>
  )
}
