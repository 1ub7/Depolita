import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <div className="flex flex-col items-center gap-3 py-24 text-center">
      <p className="text-accent font-mono text-sm">404</p>
      <h1 className="text-2xl font-bold tracking-[-0.02em]">페이지나 앱을 찾을 수 없습니다</h1>
      <Link to="/" className="text-muted hover:text-fg underline-offset-4 hover:underline">
        목록으로 돌아가기
      </Link>
    </div>
  )
}
