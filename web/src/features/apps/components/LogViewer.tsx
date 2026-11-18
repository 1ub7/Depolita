import clsx from 'clsx'
import { useEffect, useRef, useState } from 'react'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { useAppLogs } from '../queries'

const TAIL_OPTIONS = [100, 500, 1000] as const // 서버 제한: 1–1000

function lineColor(line: string) {
  if (/\b(ERROR|FATAL|Exception)\b/.test(line)) return 'text-danger'
  if (/\bWARN(ING)?\b/.test(line)) return 'text-warn'
  if (/\bStarted\b/.test(line)) return 'text-accent'
  return 'text-fg-soft'
}

export function LogViewer({ appId }: { appId: number }) {
  const [tail, setTail] = useState<number>(100)
  const [autoRefresh, setAutoRefresh] = useState(true)
  const logs = useAppLogs(appId, tail, autoRefresh)
  const lines = logs.data?.lines ?? []

  // 사용자가 맨 아래를 보고 있을 때만 새 로그를 따라 내려간다 (위로 올려서 읽는 중이면 방해하지 않음)
  const boxRef = useRef<HTMLDivElement>(null)
  const stickToBottom = useRef(true)
  useEffect(() => {
    const box = boxRef.current
    if (box && stickToBottom.current) box.scrollTop = box.scrollHeight
  }, [lines.length, logs.dataUpdatedAt])

  return (
    <Card className="overflow-hidden">
      <div className="border-line flex flex-wrap items-center justify-between gap-3 border-b px-6 py-4">
        <h2 className="text-[15px] font-semibold">컨테이너 로그</h2>
        <div className="flex flex-wrap items-center gap-2 text-xs">
          <div
            role="group"
            aria-label="가져올 줄 수"
            className="border-line bg-bg flex rounded-[10px] border p-[3px]"
          >
            {TAIL_OPTIONS.map((n) => (
              <button
                key={n}
                type="button"
                aria-pressed={tail === n}
                onClick={() => setTail(n)}
                className={clsx(
                  'cursor-pointer rounded-[7px] px-3 py-[5px] transition',
                  tail === n
                    ? 'bg-accent text-accent-ink font-semibold'
                    : 'text-label hover:text-fg',
                )}
              >
                {n}줄
              </button>
            ))}
          </div>
          <label className="text-label flex items-center gap-1.5 px-1.5">
            <input
              type="checkbox"
              checked={autoRefresh}
              onChange={(e) => setAutoRefresh(e.target.checked)}
              className="accent-accent"
            />
            자동 새로고침
          </label>
          <Button
            size="sm"
            variant="ghost"
            onClick={() => logs.refetch()}
            loading={logs.isFetching && !autoRefresh}
          >
            새로고침
          </Button>
        </div>
      </div>

      <div
        ref={boxRef}
        onScroll={(e) => {
          const el = e.currentTarget
          stickToBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 24
        }}
        className="bg-terminal h-[360px] overflow-auto py-4 font-mono text-[12.5px] leading-[1.75]"
        aria-live="off"
      >
        {logs.isPending ? (
          <p className="text-muted px-6">로그를 불러오는 중…</p>
        ) : logs.isError ? (
          <p className="text-danger-soft px-6">{logs.error.message}</p>
        ) : lines.length === 0 ? (
          <p className="text-muted px-6">아직 출력된 로그가 없습니다.</p>
        ) : (
          lines.map((line, i) => (
            <div key={i} className="grid grid-cols-[40px_1fr] gap-3.5 px-6 whitespace-pre">
              <span className="text-faint text-right select-none">{i + 1}</span>
              <span className={lineColor(line)}>{line}</span>
            </div>
          ))
        )}
      </div>
    </Card>
  )
}
