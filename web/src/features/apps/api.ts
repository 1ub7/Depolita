import { http } from '@/shared/api/http'
import type { App, AppCreateRequest, AppLogs } from './model/types'

/** server: AppController (/api/apps) */
export const appsApi = {
  list: () => http.get<App[]>('/apps').then((r) => r.data),

  get: (id: number) => http.get<App>(`/apps/${id}`).then((r) => r.data),

  logs: (id: number, tail: number) =>
    http.get<AppLogs>(`/apps/${id}/logs`, { params: { tail } }).then((r) => r.data),

  /** 202 Accepted: 복제/빌드는 서버에서 비동기로 진행된다 */
  create: (body: AppCreateRequest) => http.post<App>('/apps', body).then((r) => r.data),

  stop: (id: number) => http.post<App>(`/apps/${id}/stop`).then((r) => r.data),

  remove: (id: number) => http.delete<void>(`/apps/${id}`).then(() => undefined),
}
