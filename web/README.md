# Depolita Web

Depolita 대시보드 프론트엔드. `server/`의 REST API(`/api/apps`)를 사용한다.

## 스택

| 역할      | 선택                                                                        |
| --------- | --------------------------------------------------------------------------- |
| 빌드      | Vite 8, TypeScript 7                                                        |
| UI        | React 19, Tailwind CSS 4                                                    |
| 라우팅    | React Router                                                                |
| 서버 상태 | TanStack Query (폴링으로 배포 상태 갱신)                                    |
| HTTP      | axios (인터셉터로 `X-API-Key` 헤더 주입, ProblemDetail → `ApiError` 정규화) |
| 폼        | react-hook-form + zod (서버 `@Pattern` 규칙과 동일)                         |
| 품질      | oxlint, Prettier                                                            |

## 로컬 개발

```bash
# 1) 백엔드 (기본 8081, application-local.yml)
cd server && ./gradlew bootRun

# 2) 프론트
cd web
npm install
npm run dev        # http://localhost:5173
```

개발 서버는 `/api` 요청을 `VITE_API_PROXY_TARGET`(기본 `http://localhost:8081`)으로 넘기므로
백엔드에 CORS 설정이 필요 없다. 접속 후 `.env`의 `DEPOLITA_API_KEY` 값을 입력한다.

## 스크립트

| 명령                | 설명                                |
| ------------------- | ----------------------------------- |
| `npm run dev`       | 개발 서버                           |
| `npm run build`     | 타입 검사 + 프로덕션 빌드 (`dist/`) |
| `npm run typecheck` | 타입 검사만                         |
| `npm run lint`      | oxlint                              |
| `npm run format`    | Prettier 적용                       |

## 배포 (Docker)

멀티 스테이지 빌드 → 비루트 nginx 이미지가 정적 파일을 서빙하고 `/api`는 백엔드로 프록시한다.

```bash
# 루트에서
docker compose up -d --build web     # http://<host>:3000
```

| 환경변수       | 기본값                             | 설명                                   |
| -------------- | ---------------------------------- | -------------------------------------- |
| `API_UPSTREAM` | `http://host.docker.internal:8081` | nginx가 `/api`를 넘길 Spring Boot 주소 |
| `WEB_PORT`     | `3000`                             | 호스트에 노출할 포트                   |

## 폴더 구조

```
src/
├─ app/            # 라우터, QueryClient
├─ layouts/        # 공통 헤더 + 인증 가드
├─ pages/          # 라우트 단위 화면
├─ features/apps/  # 앱 도메인: API, 쿼리 훅, 타입, 컴포넌트
└─ shared/         # 도메인과 무관한 공용 코드 (http, auth, ui, lib)
```
