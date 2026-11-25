/** server: Depolita.domain.app.entity.AppStatus */
export const APP_STATUSES = ['CLONING', 'BUILDING', 'RUNNING', 'STOPPED', 'FAILED'] as const
export type AppStatus = (typeof APP_STATUSES)[number]

/** server: AppResponse */
export type App = {
  id: number
  name: string
  repositoryUrl: string
  internalPort: number
  assignedPort: number | null
  /** RUNNING 일 때만 값이 있다 */
  url: string | null
  status: AppStatus
  failureReason: string | null
  /** UTC ISO-8601, 예: "2026-11-18T08:00:00.123Z" */
  createdAt: string
  /** UTC ISO-8601, 예: "2026-11-18T08:00:00.123Z" */
  updatedAt: string
}

/** server: AppCreateRequest */
export type AppCreateRequest = {
  name: string
  repositoryUrl: string
  internalPort: number
}

/** server: AppLogResponse */
export type AppLogs = {
  appId: number
  lines: string[]
}

/** server: Depolita.domain.deployment.entity.DeployTrigger */
export type DeployTrigger = 'INITIAL' | 'MANUAL' | 'WEBHOOK'

/** server: Depolita.domain.deployment.entity.DeploymentStatus */
export type DeploymentStatus = 'IN_PROGRESS' | 'SUCCEEDED' | 'FAILED'

/** server: DeploymentResponse */
export type Deployment = {
  id: number
  trigger: DeployTrigger
  /** 40자 커밋 SHA. 복제 전에 실패하면 null */
  commitSha: string | null
  status: DeploymentStatus
  failureReason: string | null
  cloneMs: number | null
  buildMs: number | null
  /** UTC ISO-8601 */
  startedAt: string
  /** UTC ISO-8601. 진행 중이면 null */
  finishedAt: string | null
}

/** server: AppStatsResponse */
export type AppStats = {
  appId: number
  /** CPU 1개 = 100%. 앱 컨테이너는 0.5 CPU 로 제한되어 최대 약 50% */
  cpuPercent: number
  memoryUsageBytes: number
  memoryLimitBytes: number
}
