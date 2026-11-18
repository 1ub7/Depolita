# syntax=docker/dockerfile:1

# ---- 1단계: 빌드 ----
FROM node:22-alpine AS build
WORKDIR /app

# 의존성 파일만 먼저 복사 → 소스만 바뀌면 npm ci 레이어 캐시를 재사용
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- 2단계: 정적 파일 서빙 ----
# root 가 아닌 사용자로 도는 nginx 이미지 (기본 포트 8080)
FROM nginxinc/nginx-unprivileged:stable-alpine

# /etc/nginx/templates/*.template 은 컨테이너 시작 시 envsubst 로 환경변수가 채워진다
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

ENV API_UPSTREAM=http://host.docker.internal:8081
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -q --spider http://127.0.0.1:8080/healthz || exit 1
