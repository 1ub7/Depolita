import { QueryClient } from '@tanstack/react-query'
import { ApiError } from '@/shared/api/http'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1_000,
      // 4xx 는 다시 보내도 결과가 같으니 재시도하지 않는다
      retry: (failureCount, error) =>
        !(error instanceof ApiError && error.status >= 400 && error.status < 500) &&
        failureCount < 2,
      refetchOnWindowFocus: true,
    },
    mutations: { retry: false },
  },
})
