import { useState } from 'react'
import { Button } from '@/shared/ui/Button'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { canDelete, canRedeploy, canStop } from '../model/status'
import type { App } from '../model/types'
import { useDeleteApp, useRedeployApp, useStopApp } from '../queries'

type Props = {
  app: App
  size?: 'sm' | 'md'
  onDeleted?: () => void
}

type Pending = 'redeploy' | 'stop' | 'delete' | null

const DIALOG: Record<
  Exclude<Pending, null>,
  { title: string; description: string; label: string }
> = {
  redeploy: {
    title: '재배포',
    description: '저장소의 최신 코드로 다시 빌드해서 실행합니다.',
    label: '재배포',
  },
  stop: {
    title: '중지',
    description:
      '컨테이너를 중지하고 포트를 반납합니다. 재배포로 다시 실행할 수 있지만 접속 주소(포트)가 바뀔 수 있습니다.',
    label: '중지',
  },
  delete: {
    title: '삭제',
    description: '컨테이너와 이미지를 지우고 목록에서 제거합니다. 되돌릴 수 없습니다.',
    label: '삭제',
  },
}

function redeployNote(status: App['status']) {
  return status === 'RUNNING'
    ? '빌드하는 동안은 지금 버전이 계속 실행되지만, 빌드에 실패하면 지금 버전도 중지됩니다.'
    : '포트가 새로 할당되므로 접속 주소가 바뀔 수 있습니다.'
}

export function AppActions({ app, size = 'sm', onDeleted }: Props) {
  const [pending, setPending] = useState<Pending>(null)
  const redeploy = useRedeployApp()
  const stop = useStopApp()
  const remove = useDeleteApp()
  const error = redeploy.error ?? stop.error ?? remove.error

  const close = () => {
    setPending(null)
    redeploy.reset()
    stop.reset()
    remove.reset()
  }

  const confirm = () => {
    if (pending === 'redeploy') redeploy.mutate(app.id, { onSuccess: close })
    if (pending === 'stop') stop.mutate(app.id, { onSuccess: close })
    if (pending === 'delete')
      remove.mutate(app.id, {
        onSuccess: () => {
          close()
          onDeleted?.()
        },
      })
  }

  const dialog = pending ? DIALOG[pending] : null

  return (
    <>
      <Button
        size={size}
        variant="ghost"
        disabled={!canRedeploy(app.status)}
        title={canRedeploy(app.status) ? undefined : '배포가 끝난 뒤에 재배포할 수 있습니다'}
        onClick={() => setPending('redeploy')}
      >
        재배포
      </Button>
      <Button
        size={size}
        variant="ghost"
        disabled={!canStop(app.status)}
        title={canStop(app.status) ? undefined : '실행 중인 앱만 중지할 수 있습니다'}
        onClick={() => setPending('stop')}
      >
        중지
      </Button>
      <Button
        size={size}
        variant="danger"
        disabled={!canDelete(app.status)}
        title={canDelete(app.status) ? undefined : '배포가 끝난 뒤에 삭제할 수 있습니다'}
        onClick={() => setPending('delete')}
      >
        삭제
      </Button>

      <ConfirmDialog
        open={dialog !== null}
        title={dialog ? `${app.name} ${dialog.title}` : ''}
        description={
          <>
            {dialog?.description}
            {pending === 'redeploy' && ` ${redeployNote(app.status)}`}
            {error && <p className="text-danger-soft mt-3">{error.message}</p>}
          </>
        }
        confirmLabel={dialog?.label ?? ''}
        danger={pending === 'delete'}
        loading={redeploy.isPending || stop.isPending || remove.isPending}
        onConfirm={confirm}
        onClose={close}
      />
    </>
  )
}
