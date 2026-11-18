import clsx from 'clsx'
import type { ComponentProps } from 'react'

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost'
type Size = 'sm' | 'md'

const VARIANT: Record<Variant, string> = {
  primary:
    'bg-accent font-semibold text-accent-ink shadow-[0_0_24px_rgba(61,255,138,0.25)] hover:brightness-110',
  secondary: 'border border-line-strong bg-surface-2 text-fg hover:bg-[#222826]',
  danger: 'border border-danger/25 bg-danger/10 text-[#ff7a7a] hover:bg-danger/15',
  ghost: 'bg-surface-2 text-fg hover:bg-[#222826]',
}

const SIZE: Record<Size, string> = {
  sm: 'rounded-lg px-3 py-1.5 text-xs',
  md: 'min-h-11 rounded-[10px] px-4 py-2.5 text-sm',
}

type Props = ComponentProps<'button'> & { variant?: Variant; size?: Size; loading?: boolean }

export function Button({
  variant = 'secondary',
  size = 'md',
  loading = false,
  disabled,
  className,
  children,
  type = 'button',
  ...rest
}: Props) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={clsx(
        'inline-flex cursor-pointer items-center justify-center gap-2 font-medium transition',
        'disabled:cursor-not-allowed disabled:opacity-40 disabled:shadow-none disabled:hover:brightness-100',
        VARIANT[variant],
        SIZE[size],
        className,
      )}
      {...rest}
    >
      {loading && (
        <span className="size-3.5 animate-spin rounded-full border-2 border-current border-t-transparent" />
      )}
      {children}
    </button>
  )
}
