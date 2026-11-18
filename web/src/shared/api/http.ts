import axios, { AxiosError } from 'axios'
import { apiKeyStore } from '@/shared/auth/api-key'

export const API_KEY_HEADER = 'X-API-Key'

/** GlobalExceptionHandler 가 내려주는 ProblemDetail(RFC 9457) 형태 */
export type ProblemDetail = {
  title?: string
  status?: number
  detail?: string
  code?: string
  errors?: { field: string; reason: string }[]
}

/** 화면에서 다루기 쉽게 정규화한 에러 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: { field: string; reason: string }[]

  constructor(status: number, code: string, message: string, fieldErrors: ProblemDetail['errors']) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors ?? []
  }
}

export const http = axios.create({
  baseURL: '/api',
  timeout: 15_000,
})

http.interceptors.request.use((config) => {
  const key = apiKeyStore.get()
  if (key) config.headers.set(API_KEY_HEADER, key)
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    const apiError = toApiError(error)
    // 키가 틀렸거나 서버 키가 바뀌었으면 저장된 키를 지운다 → RequireApiKey 가 /login 으로 보낸다
    if (apiError.status === 401) apiKeyStore.clear()
    return Promise.reject(apiError)
  },
)

function toApiError(error: unknown): ApiError {
  if (error instanceof AxiosError) {
    if (!error.response) {
      return new ApiError(0, 'NETWORK_ERROR', '서버에 연결할 수 없습니다.', [])
    }
    const body = error.response.data as ProblemDetail | undefined
    return new ApiError(
      error.response.status,
      body?.code ?? 'UNKNOWN',
      body?.detail ?? '요청을 처리하지 못했습니다.',
      body?.errors,
    )
  }
  return new ApiError(0, 'UNKNOWN', '알 수 없는 오류가 발생했습니다.', [])
}
