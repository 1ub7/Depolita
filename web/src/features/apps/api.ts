import { http } from '@/shared/api/http'
import type { App, AppCreateRequest, AppLogs, AppStats, Deployment } from './model/types'

/** server: AppController (/api/apps) */
export const appsApi = {
  list: () => http.get<App[]>('/apps').then((r) => r.data),

  get: (id: number) => http.get<App>(`/apps/${id}`).then((r) => r.data),

  logs: (id: number, tail: number) =>
    http.get<AppLogs>(`/apps/${id}/logs`, { params: { tail } }).then((r) => r.data),

  /** 실행 중인 앱만. 아니면 409 */
  stats: (id: number) => http.get<AppStats>(`/apps/${id}/stats`).then((r) => r.data),

  /** 최근 20건, 최신순 */
  deployments: (id: number) =>
    http.get<Deployment[]>(`/apps/${id}/deployments`).then((r) => r.data),

  /** 202 Accepted: 복제/빌드는 서버에서 비동기로 진행된다 */
  create: (body: AppCreateRequest) => http.post<App>('/apps', body).then((r) => r.data),

  stop: (id: number) => http.post<App>(`/apps/${id}/stop`).then((r) => r.data),

  /** 202 Accepted: 같은 설정으로 다시 복제·빌드·실행한다 */
  redeploy: (id: number) => http.post<App>(`/apps/${id}/redeploy`).then((r) => r.data),

  remove: (id: number) => http.delete<void>(`/apps/${id}`).then(() => undefined),
}
