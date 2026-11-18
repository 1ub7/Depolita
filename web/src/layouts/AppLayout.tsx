import clsx from 'clsx'
import { Link, Navigate, Outlet, useLocation } from 'react-router'
import { useApps } from '@/features/apps/queries'
import { apiKeyStore, useApiKey } from '@/shared/auth/api-key'
import { Logo } from '@/shared/ui/Logo'

/** 키가 없으면 /login 으로 보내고, 있으면 공통 헤더와 함께 하위 페이지를 그린다 */
export function AppLayout() {
  const apiKey = useApiKey()
  const location = useLocation()

  if (!apiKey) return <Navigate to="/login" replace state={{ from: location.pathname }} />

  return (
    <div className="min-h-dvh">
      <Header />
      <main className="mx-auto flex max-w-[1200px] flex-col gap-8 px-5 pt-10 pb-16 md:px-8">
        <Outlet />
      </main>
    </div>
  )
}

function Header() {
  // 목록 쿼리를 같이 구독해서 서버 연결 상태를 표시한다 (같은 키라 요청이 중복되지 않음)
  const { isError } = useApps()

  return (
    <header className="border-line border-b">
      <div className="mx-auto flex max-w-[1200px] flex-wrap items-center justify-between gap-4 px-5 py-4 md:px-8">
        <Link to="/" aria-label="Depolita 홈">
          <Logo />
        </Link>
        <nav className="flex flex-wrap items-center gap-3 text-[13px]">
          <span className="border-line bg-surface text-muted flex items-center gap-2 rounded-full border px-3 py-1.5">
            <span
              className={clsx(
                'size-[7px] rounded-full',
                isError ? 'bg-danger' : 'bg-accent shadow-[0_0_8px_var(--color-accent)]',
              )}
            />
            {isError ? '서버 연결 끊김' : '서버 연결됨'}
          </span>
          <button
            type="button"
            onClick={() => apiKeyStore.clear()}
            className="border-line-strong bg-surface text-fg hover:border-faint cursor-pointer rounded-[10px] border px-3.5 py-2"
          >
            API 키 변경
          </button>
        </nav>
      </div>
    </header>
  )
}
