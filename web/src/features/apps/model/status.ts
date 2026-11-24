import type { AppStatus } from './types'

type Tone = 'accent' | 'warn' | 'danger' | 'idle'

export const STATUS_META: Record<AppStatus, { label: string; tone: Tone }> = {
  CLONING: { label: '복제 중', tone: 'warn' },
  BUILDING: { label: '빌드 중', tone: 'warn' },
  RUNNING: { label: '실행 중', tone: 'accent' },
  STOPPED: { label: '중지됨', tone: 'idle' },
  FAILED: { label: '실패', tone: 'danger' },
}

/** 서버 AppStatus.isInProgress() 와 같은 규칙 */
export const isInProgress = (status: AppStatus) => status === 'CLONING' || status === 'BUILDING'

/** 서버 AppStatus.canTransitionTo(STOPPED): RUNNING 에서만 중지 가능 */
export const canStop = (status: AppStatus) => status === 'RUNNING'

/** 서버 AppService.redeploy: 진행 중이 아니면 언제든 재배포 가능 */
export const canRedeploy = (status: AppStatus) => !isInProgress(status)

/** 서버 AppService.delete: 진행 중이면 INVALID_APP_STATUS */
export const canDelete = (status: AppStatus) => !isInProgress(status)

/** Tailwind 는 클래스 이름을 정적으로 찾으므로 문자열을 조합하지 않고 전부 적어둔다 */
export const TONE_CLASS: Record<Tone, { text: string; bg: string; dot: string }> = {
  accent: {
    text: 'text-accent',
    bg: 'bg-accent/10',
    dot: 'bg-accent shadow-[0_0_6px_var(--color-accent)]',
  },
  warn: { text: 'text-warn', bg: 'bg-warn/10', dot: 'bg-warn' },
  danger: { text: 'text-danger', bg: 'bg-danger/10', dot: 'bg-danger' },
  idle: { text: 'text-idle', bg: 'bg-idle/10', dot: 'bg-idle' },
}
