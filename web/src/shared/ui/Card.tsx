import clsx from 'clsx'
import type { ComponentProps } from 'react'

export function Card({ className, ...rest }: ComponentProps<'section'>) {
  return (
    <section className={clsx('border-line bg-surface rounded-2xl border', className)} {...rest} />
  )
}
