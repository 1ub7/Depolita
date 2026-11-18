import { useEffect, useRef, type ReactNode } from 'react'
import { Button } from './Button'

type Props = {
  open: boolean
  title: string
  description: ReactNode
  confirmLabel: string
  danger?: boolean
  loading?: boolean
  onConfirm: () => void
  onClose: () => void
}

/** 브라우저 기본 <dialog> 사용: 포커스 가두기와 ESC 닫기를 브라우저가 처리한다 */
export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  danger = false,
  loading = false,
  onConfirm,
  onClose,
}: Props) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      className="border-line bg-surface text-fg m-auto w-[min(420px,calc(100%-32px))] rounded-2xl border p-0 shadow-[0_24px_60px_rgba(0,0,0,0.5)] backdrop:bg-black/60"
    >
      <div className="flex flex-col gap-5 p-6">
        <div className="flex flex-col gap-1.5">
          <h2 className="text-base font-semibold">{title}</h2>
          <div className="text-muted text-[13px]">{description}</div>
        </div>
        <div className="flex justify-end gap-2">
          <Button onClick={onClose} disabled={loading}>
            취소
          </Button>
          <Button variant={danger ? 'danger' : 'primary'} onClick={onConfirm} loading={loading}>
            {confirmLabel}
          </Button>
        </div>
      </div>
    </dialog>
  )
}
