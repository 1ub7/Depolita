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
  createdAt: string
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
