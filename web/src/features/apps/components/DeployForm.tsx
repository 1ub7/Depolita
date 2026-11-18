import { zodResolver } from '@hookform/resolvers/zod'
import clsx from 'clsx'
import { useForm } from 'react-hook-form'
import { ApiError } from '@/shared/api/http'
import { Button } from '@/shared/ui/Button'
import { Card } from '@/shared/ui/Card'
import { deploySchema, type DeployFormInput, type DeployFormValues } from '../model/schema'
import { useDeployApp } from '../queries'

const FIELDS = ['name', 'repositoryUrl', 'internalPort'] as const
type FieldName = (typeof FIELDS)[number]
const isField = (value: string): value is FieldName => (FIELDS as readonly string[]).includes(value)

const inputClass =
  'w-full rounded-[10px] border border-line-strong bg-bg px-3.5 py-3 text-fg outline-none transition placeholder:text-[#5e6662] focus:border-accent focus:ring-[3px] focus:ring-accent/15 aria-invalid:border-danger/60'

export function DeployForm() {
  const deploy = useDeployApp()
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<DeployFormInput, unknown, DeployFormValues>({
    resolver: zodResolver(deploySchema),
    defaultValues: { name: '', repositoryUrl: '', internalPort: '' },
  })

  const onSubmit = handleSubmit((values) =>
    deploy.mutateAsync(values).then(
      () => reset(),
      (error: unknown) => {
        if (!(error instanceof ApiError)) throw error
        // 서버 검증 실패(INVALID_INPUT)는 해당 입력칸 아래에 보여준다
        const fieldErrors = error.fieldErrors.filter((fe) => isField(fe.field))
        fieldErrors.forEach((fe) => {
          if (isField(fe.field)) setError(fe.field, { message: fe.reason })
        })
        if (fieldErrors.length === 0) setError('root.server', { message: error.message })
      },
    ),
  )

  return (
    <Card className="flex flex-col gap-[18px] p-6">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-base font-semibold">새 배포</h2>
        <span className="text-muted text-[13px]">Clone → Build → Run</span>
      </div>

      <form onSubmit={onSubmit} noValidate className="grid grid-cols-1 gap-4 md:grid-cols-12">
        <Field label="앱 이름" error={errors.name?.message} className="md:col-span-3">
          <input
            {...register('name')}
            placeholder="todo-api"
            autoComplete="off"
            aria-invalid={!!errors.name}
            className={inputClass}
          />
        </Field>
        <Field
          label="GitHub 저장소"
          error={errors.repositoryUrl?.message}
          className="md:col-span-6"
        >
          <input
            {...register('repositoryUrl')}
            type="url"
            placeholder="https://github.com/owner/repo"
            autoComplete="off"
            aria-invalid={!!errors.repositoryUrl}
            className={inputClass}
          />
        </Field>
        <Field label="포트" error={errors.internalPort?.message} className="md:col-span-1">
          <input
            {...register('internalPort')}
            inputMode="numeric"
            placeholder="8080"
            aria-invalid={!!errors.internalPort}
            className={inputClass}
          />
        </Field>
        <div className="flex flex-col gap-2 md:col-span-2">
          {/* 라벨 높이만큼 띄워서 입력칸과 버튼의 아래선을 맞춘다 */}
          <span className="hidden text-[13px] md:block">&nbsp;</span>
          <Button type="submit" variant="primary" loading={deploy.isPending} className="w-full">
            배포하기
          </Button>
        </div>
      </form>

      {errors.root?.server && (
        <p
          role="alert"
          className="bg-danger/10 text-danger-soft rounded-[10px] px-3.5 py-2.5 text-[13px]"
        >
          {errors.root.server.message}
        </p>
      )}
      {deploy.isSuccess && !errors.root?.server && (
        <p role="status" className="text-accent text-[13px]">
          {deploy.data.name} 배포를 시작했습니다. 아래 목록에서 진행 상황을 확인하세요.
        </p>
      )}
      <p className="text-muted text-xs">
        소문자·숫자·하이픈 3–50자 · 저장소 루트에 Dockerfile 필요 · 허용된 GitHub 계정만 배포 · 외부
        포트는 10001–10999에서 자동 할당
      </p>
    </Card>
  )
}

function Field({
  label,
  error,
  className,
  children,
}: {
  label: string
  error?: string
  className?: string
  children: React.ReactNode
}) {
  return (
    <label className={clsx('text-label flex flex-col gap-2 text-[13px]', className)}>
      <span>{label}</span>
      {children}
      {error && <span className="text-danger-soft text-xs">{error}</span>}
    </label>
  )
}
