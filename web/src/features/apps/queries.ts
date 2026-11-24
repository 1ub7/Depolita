import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { appsApi } from './api'
import { isInProgress } from './model/status'
import type { App, AppCreateRequest } from './model/types'

/** 쿼리 키를 한곳에서 관리해야 invalidate 할 때 오타가 안 난다 */
export const appKeys = {
  all: ['apps'] as const,
  list: () => [...appKeys.all, 'list'] as const,
  detail: (id: number) => [...appKeys.all, 'detail', id] as const,
  logs: (id: number, tail: number) => [...appKeys.all, 'logs', id, tail] as const,
  deployments: (id: number) => [...appKeys.all, 'deployments', id] as const,
}

// 배포가 진행 중이면 상태가 자주 바뀌므로 더 자주 물어본다
const FAST_POLL = 2_000
const SLOW_POLL = 5_000

export function useApps() {
  return useQuery({
    queryKey: appKeys.list(),
    queryFn: appsApi.list,
    refetchInterval: (query) =>
      query.state.data?.some((app) => isInProgress(app.status)) ? FAST_POLL : SLOW_POLL,
  })
}

export function useApp(id: number) {
  return useQuery({
    queryKey: appKeys.detail(id),
    queryFn: () => appsApi.get(id),
    refetchInterval: (query) =>
      query.state.data && isInProgress(query.state.data.status) ? FAST_POLL : SLOW_POLL,
  })
}

export function useAppLogs(id: number, tail: number, autoRefresh: boolean) {
  return useQuery({
    queryKey: appKeys.logs(id, tail),
    queryFn: () => appsApi.logs(id, tail),
    refetchInterval: autoRefresh ? 3_000 : false,
    // tail 을 바꿔도 이전 로그를 보여주다가 교체 → 화면 깜빡임 방지
    placeholderData: (prev) => prev,
  })
}

export function useDeployments(id: number) {
  return useQuery({
    queryKey: appKeys.deployments(id),
    queryFn: () => appsApi.deployments(id),
    // 진행 중인 배포가 있으면 단계별 소요 시간이 채워지는 걸 빨리 보여준다
    refetchInterval: (query) =>
      query.state.data?.some((d) => d.status === 'IN_PROGRESS') ? FAST_POLL : SLOW_POLL,
  })
}

export function useDeployApp() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: AppCreateRequest) => appsApi.create(body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: appKeys.list() }),
  })
}

export function useStopApp() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => appsApi.stop(id),
    onSuccess: (app: App) => {
      queryClient.setQueryData(appKeys.detail(app.id), app)
      return queryClient.invalidateQueries({ queryKey: appKeys.list() })
    },
  })
}

export function useRedeployApp() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => appsApi.redeploy(id),
    onSuccess: (app: App) => {
      queryClient.setQueryData(appKeys.detail(app.id), app)
      // 새 배포 이력(진행 중)이 다음 폴링을 기다리지 않고 바로 보이도록
      void queryClient.invalidateQueries({ queryKey: appKeys.deployments(app.id) })
      return queryClient.invalidateQueries({ queryKey: appKeys.list() })
    },
  })
}

export function useDeleteApp() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => appsApi.remove(id),
    onSuccess: (_data, id) => {
      queryClient.removeQueries({ queryKey: appKeys.detail(id) })
      return queryClient.invalidateQueries({ queryKey: appKeys.list() })
    },
  })
}
