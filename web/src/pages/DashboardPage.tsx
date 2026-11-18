import { AppTable } from '@/features/apps/components/AppTable'
import { DeployForm } from '@/features/apps/components/DeployForm'
import { StatsCards } from '@/features/apps/components/StatsCards'
import { useApps } from '@/features/apps/queries'
import { Card } from '@/shared/ui/Card'

export function DashboardPage() {
  const apps = useApps()

  return (
    <>
      <div className="flex flex-col gap-1.5">
        <h1 className="text-[28px] font-bold tracking-[-0.025em]">Applications</h1>
        <p className="text-muted">
          GitHub 저장소 주소만 넣으면 빌드부터 실행까지 자동으로 처리합니다.
        </p>
      </div>

      <StatsCards apps={apps.data ?? []} />
      <DeployForm />

      {apps.isPending ? (
        <Card className="h-60 animate-pulse" aria-label="앱 목록 불러오는 중" />
      ) : apps.isError ? (
        <Card className="text-danger-soft px-6 py-10 text-center">{apps.error.message}</Card>
      ) : (
        <AppTable apps={apps.data} isFetching={apps.isFetching} />
      )}
    </>
  )
}
