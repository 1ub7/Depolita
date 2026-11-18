import { useSyncExternalStore } from 'react'

/**
 * 서버(ApiKeyFilter)는 X-API-Key 헤더 하나로 인증한다.
 * 키는 탭을 닫으면 사라지도록 sessionStorage 에 둔다.
 * ⚠️ 브라우저에 비밀값을 두는 방식이라 XSS 에 취약하다. 실서비스라면 세션 쿠키나 OAuth 로 바꿔야 한다.
 */
const STORAGE_KEY = 'depolita.apiKey'

type Listener = () => void
const listeners = new Set<Listener>()

function read(): string | null {
  try {
    return sessionStorage.getItem(STORAGE_KEY)
  } catch {
    return null
  }
}

function emit() {
  listeners.forEach((listener) => listener())
}

export const apiKeyStore = {
  get: read,
  set(key: string) {
    try {
      sessionStorage.setItem(STORAGE_KEY, key)
    } finally {
      emit()
    }
  },
  clear() {
    try {
      sessionStorage.removeItem(STORAGE_KEY)
    } finally {
      emit()
    }
  },
  subscribe(listener: Listener) {
    listeners.add(listener)
    return () => listeners.delete(listener)
  },
}

export function useApiKey() {
  return useSyncExternalStore(apiKeyStore.subscribe, apiKeyStore.get, () => null)
}
