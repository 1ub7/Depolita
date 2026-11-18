import { useState } from 'react'
import { Button } from '@/shared/ui/Button'
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog'
import { canDelete, canStop } from '../model/status'
import type { App } from '../model/types'
import { useDeleteApp, useStopApp } from '../queries'

type Props = {
  app: App
  size?: 'sm' | 'md'
  onDeleted?: () => void
}

type Pending = 'stop' | 'delete' | null

export function AppActions({ app, size = 'sm', onDeleted }: Props) {
  const [pending, setPending] = useState<Pending>(null)
  const stop = useStopApp()
  const remove = useDeleteApp()
  const error = stop.error ?? remove.error

  const close = () => {
    setPending(null)
    stop.reset()
    remove.reset()
  }

  const confirm = () => {
    if (pending === 'stop') stop.mutate(app.id, { onSuccess: close })
    if (pending === 'delete')
      remove.mutate(app.id, {
        onSuccess: () => {
          close()
          onDeleted?.()
        },
      })
  }

  return (
    <>
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
        open={pending !== null}
        title={pending === 'delete' ? `${app.name} 삭제` : `${app.name} 중지`}
        description={
          <>
            {pending === 'delete'
              ? '컨테이너와 이미지를 지우고 목록에서 제거합니다. 되돌릴 수 없습니다.'
              : '컨테이너를 중지합니다. 중지한 앱은 다시 실행할 수 없고 새로 배포해야 합니다.'}
            {error && <p className="text-danger-soft mt-3">{error.message}</p>}
          </>
        }
        confirmLabel={pending === 'delete' ? '삭제' : '중지'}
        danger={pending === 'delete'}
        loading={stop.isPending || remove.isPending}
        onConfirm={confirm}
        onClose={close}
      />
    </>
  )
}
