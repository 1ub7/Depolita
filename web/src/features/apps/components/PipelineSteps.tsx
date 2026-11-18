import clsx from 'clsx'
import { Card } from '@/shared/ui/Card'
import type { App, AppStatus } from '../model/types'

type StepState = 'done' | 'active' | 'pending' | 'stopped' | 'unknown'

const STEPS = [
  { title: '저장소 복제', tool: 'JGit' },
  { title: '이미지 빌드', tool: 'docker-java' },
  { title: '컨테이너 실행', tool: 'Docker' },
] as const

/**
 * 서버는 "몇 번째 단계에서 실패했는지"를 따로 저장하지 않는다.
 * 그래서 FAILED 면 단계를 추측하지 않고 'unknown' 으로 두고 실패 사유만 보여준다.
 */
function stepStates(status: AppStatus): StepState[] {
  switch (status) {
    case 'CLONING':
      return ['active', 'pending', 'pending']
    case 'BUILDING':
      return ['done', 'active', 'pending']
    case 'RUNNING':
      return ['done', 'done', 'active']
    case 'STOPPED':
      return ['done', 'done', 'stopped']
    case 'FAILED':
      return ['unknown', 'unknown', 'unknown']
  }
}

const STATE_TEXT: Record<StepState, string> = {
  done: '완료',
  active: '진행 중',
  pending: '대기',
  stopped: '중지됨',
  unknown: '—',
}

export function PipelineSteps({ app }: { app: App }) {
  const states = stepStates(app.status)

  return (
    <Card className="flex flex-col gap-4 px-6 py-[22px]">
      <h2 className="text-[15px] font-semibold">배포 단계</h2>
      <ol className="grid grid-cols-1 gap-3 md:grid-cols-3">
        {STEPS.map((step, i) => {
          const state = states[i] ?? 'pending'
          const isLastActive = state === 'active' && i === STEPS.length - 1
          const desc =
            isLastActive && app.assignedPort
              ? `포트 ${app.assignedPort} 할당`
              : `${step.tool} · ${STATE_TEXT[state]}`
          return (
            <li
              key={step.title}
              className={clsx(
                'flex items-center gap-3 rounded-xl border px-4 py-3.5',
                isLastActive ? 'border-accent/35 bg-accent/8' : 'border-line bg-surface-sunken',
              )}
            >
              <StepIcon state={state} running={isLastActive} />
              <div className="flex flex-col">
                <span
                  className={clsx(
                    'font-semibold',
                    isLastActive
                      ? 'text-accent'
                      : state === 'pending' || state === 'unknown'
                        ? 'text-muted'
                        : 'text-fg',
                  )}
                >
                  {step.title}
                </span>
                <span className="text-muted text-xs">{desc}</span>
              </div>
            </li>
          )
        })}
      </ol>
      {app.status === 'FAILED' && (
        <p
          role="alert"
          className="border-danger/25 bg-danger/8 text-danger-soft rounded-[10px] border px-4 py-3 text-[13px]"
        >
          배포 실패: {app.failureReason ?? '사유가 기록되지 않았습니다.'}
        </p>
      )}
    </Card>
  )
}

function StepIcon({ state, running }: { state: StepState; running: boolean }) {
  const base = 'flex size-7 shrink-0 items-center justify-center rounded-full text-[13px] font-bold'
  if (state === 'done' || running)
    return (
      <span className={clsx(base, 'bg-accent text-accent-ink')} aria-hidden="true">
        {running ? '●' : '✓'}
      </span>
    )
  if (state === 'active')
    return (
      <span
        className={clsx(base, 'border-warn animate-spin border-2 border-t-transparent')}
        aria-hidden="true"
      />
    )
  if (state === 'stopped')
    return (
      <span className={clsx(base, 'bg-idle/20 text-idle')} aria-hidden="true">
        ■
      </span>
    )
  return <span className={clsx(base, 'border-line-strong text-faint border')} aria-hidden="true" />
}
