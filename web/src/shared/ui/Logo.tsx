type Props = { size?: number; withText?: boolean }

/** 위로 쌓이는 두 꺾쇠: 아래(회색)에서 위(초록)로 올라가는 배포 흐름 */
export function Logo({ size = 28, withText = true }: Props) {
  return (
    <span className="inline-flex items-center gap-2.5">
      <svg width={size} height={size} viewBox="0 0 28 28" aria-hidden="true">
        <path
          d="M7 20.5l7-6.5 7 6.5"
          fill="none"
          stroke="#4A524E"
          strokeWidth="3"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <path
          d="M7 13.5L14 7l7 6.5"
          fill="none"
          stroke="var(--color-accent)"
          strokeWidth="3"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
      {withText && <span className="text-[17px] font-bold tracking-[-0.02em]">Depolita</span>}
    </span>
  )
}
