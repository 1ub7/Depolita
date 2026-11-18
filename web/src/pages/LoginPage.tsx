import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { ApiError, API_KEY_HEADER, http } from '@/shared/api/http'
import { apiKeyStore, useApiKey } from '@/shared/auth/api-key'
import { Button } from '@/shared/ui/Button'
import { Logo } from '@/shared/ui/Logo'

export function LoginPage() {
  const apiKey = useApiKey()
  const navigate = useNavigate()
  const from = (useLocation().state as { from?: string } | null)?.from ?? '/'

  const [value, setValue] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  if (apiKey) return <Navigate to={from} replace />

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    const key = value.trim()
    if (!key) return setError('API 키를 입력하세요.')

    setLoading(true)
    setError(null)
    try {
      // 저장하기 전에 실제로 통하는 키인지 한 번 확인한다
      await http.get('/apps', { headers: { [API_KEY_HEADER]: key } })
      apiKeyStore.set(key)
      navigate(from, { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '연결에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-dvh items-center justify-center bg-[radial-gradient(600px_300px_at_50%_0%,rgba(61,255,138,0.07),transparent_70%)] px-5 py-12">
      <form
        onSubmit={onSubmit}
        className="border-line bg-surface flex w-full max-w-[420px] flex-col gap-6 rounded-[20px] border px-8 py-9 shadow-[0_24px_60px_rgba(0,0,0,0.45)]"
      >
        <div className="flex flex-col items-center gap-3.5 text-center">
          <Logo size={48} withText={false} />
          <div className="flex flex-col gap-1">
            <h1 className="text-[22px] font-bold tracking-[-0.02em]">Depolita에 연결</h1>
            <p className="text-muted text-[13px]">서버에 설정된 API 키를 입력하세요.</p>
          </div>
        </div>

        <label className="text-label flex flex-col gap-2 text-[13px]">
          <span>API 키</span>
          <input
            type="password"
            value={value}
            onChange={(e) => setValue(e.target.value)}
            placeholder="X-API-Key"
            autoComplete="current-password"
            autoFocus
            aria-invalid={!!error}
            className="border-line-strong bg-bg text-fg focus:border-accent focus:ring-accent/15 rounded-[10px] border px-3.5 py-3 transition outline-none placeholder:text-[#5e6662] focus:ring-[3px]"
          />
        </label>

        {error && (
          <p
            role="alert"
            className="border-danger/25 bg-danger/8 text-danger-soft flex items-start gap-2.5 rounded-[10px] border px-3 py-2.5 text-[13px]"
          >
            <svg
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
              className="mt-0.5 shrink-0"
            >
              <circle cx="12" cy="12" r="9" />
              <path d="M12 8v5M12 16h.01" />
            </svg>
            {error}
          </p>
        )}

        <Button type="submit" variant="primary" loading={loading}>
          연결하기
        </Button>
        <p className="text-subtle text-center text-xs">모든 요청에 X-API-Key 헤더로 전송됩니다.</p>
      </form>
    </div>
  )
}
