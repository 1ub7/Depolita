import { Link, useNavigate, useParams } from 'react-router'
import { AppActions } from '@/features/apps/components/AppActions'
import { DeploymentHistory } from '@/features/apps/components/DeploymentHistory'
import { LogViewer } from '@/features/apps/components/LogViewer'
import { PipelineSteps } from '@/features/apps/components/PipelineSteps'
import { StatusBadge } from '@/features/apps/components/StatusBadge'
import { useApp } from '@/features/apps/queries'
import { ApiError } from '@/shared/api/http'
import { formatDateTime, shortRepo } from '@/shared/lib/format'
import { Card } from '@/shared/ui/Card'
import { NotFoundPage } from './NotFoundPage'

export function AppDetailPage() {
  const appId = Number(useParams().appId)
  if (!Number.isInteger(appId) || appId <= 0) return <NotFoundPage />
  // key 로 id 가 바뀌면 상태(로그 tail 등)를 새로 시작한다
  return <AppDetail key={appId} appId={appId} />
}

function AppDetail({ appId }: { appId: number }) {
  const navigate = useNavigate()
  const { data: app, error, isPending } = useApp(appId)

  if (error instanceof ApiError && error.status === 404) return <NotFoundPage />
  if (isPending) return <Card className="h-80 animate-pulse" aria-label="불러오는 중" />
  if (!app) return <Card className="text-danger-soft px-6 py-10 text-center">{error?.message}</Card>

  const info = [
    { k: '저장소', v: shortRepo(app.repositoryUrl) },
    { k: '내부 포트', v: String(app.internalPort) },
    { k: '할당 포트', v: app.assignedPort ? String(app.assignedPort) : '—' },
    { k: '앱 ID', v: String(app.id) },
    { k: '생성', v: formatDateTime(app.createdAt) },
    { k: '마지막 변경', v: formatDateTime(app.updatedAt) },
  ]

  return (
    <>
      <nav aria-label="breadcrumb" className="text-muted -mt-3 text-[13px]">
        <Link to="/" className="hover:text-fg">
          Applications
        </Link>
        <span className="text-faint mx-1.5">/</span>
        {app.name}
      </nav>

      <section className="-mt-2 flex flex-wrap items-end justify-between gap-5">
        <div className="flex flex-col gap-2.5">
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-[28px] font-bold tracking-[-0.025em]">{app.name}</h1>
            <StatusBadge status={app.status} />
          </div>
          {app.url ? (
            <a
              href={app.url}
              target="_blank"
              rel="noreferrer"
              className="text-accent inline-flex w-fit items-center gap-1.5 font-medium hover:underline"
            >
              {app.url}
              <svg
                width="14"
                height="14"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
              >
                <path d="M7 17L17 7M9 7h8v8" />
              </svg>
            </a>
          ) : (
            <span className="text-muted">실행 중일 때 접속 주소가 표시됩니다.</span>
          )}
        </div>
        <div className="flex gap-2">
          <AppActions app={app} size="md" onDeleted={() => navigate('/', { replace: true })} />
        </div>
      </section>

      <PipelineSteps app={app} />

      <Card className="py-2">
        <dl className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3">
          {info.map((row) => (
            <div key={row.k} className="flex flex-col gap-1 px-6 py-3.5">
              <dt className="text-muted text-xs">{row.k}</dt>
              <dd className="font-medium [overflow-wrap:anywhere]">{row.v}</dd>
            </div>
          ))}
        </dl>
      </Card>

      <DeploymentHistory appId={app.id} repositoryUrl={app.repositoryUrl} />

      <LogViewer appId={app.id} />
    </>
  )
}
