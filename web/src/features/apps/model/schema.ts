import { z } from 'zod'

/**
 * 서버 AppCreateRequest 의 @Pattern / @Min / @Max 와 같은 규칙.
 * 프론트 검증은 UX 용이고, 최종 검증은 항상 서버가 한다.
 */
export const deploySchema = z.object({
  name: z
    .string()
    .trim()
    .regex(
      /^[a-z0-9][a-z0-9-]{1,48}[a-z0-9]$/,
      '소문자, 숫자, 하이픈만 가능하며 3-50자여야 합니다.',
    ),
  repositoryUrl: z
    .string()
    .trim()
    .regex(
      /^https:\/\/github\.com\/[A-Za-z0-9-]+\/[A-Za-z0-9._-]+?(\.git)?\/?$/,
      'GitHub HTTPS 저장소 URL만 허용됩니다.',
    ),
  // <input> 값은 항상 문자열이라, 빈 값부터 거른 뒤 숫자로 바꾼다
  internalPort: z
    .string()
    .trim()
    .min(1, '포트를 입력하세요.')
    .pipe(
      z.coerce
        .number<string>({ error: '숫자만 입력하세요.' })
        .int('정수만 입력하세요.')
        .min(1, '1–65535 사이여야 합니다.')
        .max(65535, '1–65535 사이여야 합니다.'),
    ),
})

export type DeployFormInput = z.input<typeof deploySchema>
export type DeployFormValues = z.output<typeof deploySchema>
