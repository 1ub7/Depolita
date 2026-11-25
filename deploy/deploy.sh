#!/usr/bin/env bash
# EC2 에서 GHCR 이미지로 Depolita 를 배포한다.
# - 자동: GitHub Actions(deploy.yml) → SSM 문서 depolita-deploy 가 이 스크립트를 실행
# - 수동: sudo bash deploy/deploy.sh <커밋 SHA 40자>
#
# 새 버전이 헬스체크를 통과하지 못하면 직전 버전으로 되돌리고 실패로 끝낸다.
set -euo pipefail

TAG="${1:?사용법: deploy.sh <이미지 태그(커밋 SHA)>}"
cd "$(dirname "$0")/.."

COMPOSE=(docker compose -f docker-compose.prod.yml)
STATE_FILE=/home/ubuntu/.depolita-deployed-tag
HEALTH_URL=http://127.0.0.1:8081/actuator/health
PREVIOUS="$(cat "$STATE_FILE" 2>/dev/null || true)"

up() {
  IMAGE_TAG="$1" "${COMPOSE[@]}" pull server web
  IMAGE_TAG="$1" "${COMPOSE[@]}" up -d --no-build
}

wait_healthy() {
  # Spring Boot 기동 + Flyway 마이그레이션까지 최대 약 3분 기다린다
  for _ in $(seq 1 36); do
    if curl -fsS "$HEALTH_URL" >/dev/null 2>&1; then
      return 0
    fi
    sleep 5
  done
  return 1
}

echo "[deploy] ${PREVIOUS:-없음} → ${TAG}"
up "$TAG"

if wait_healthy; then
  echo "$TAG" > "$STATE_FILE"
  docker image prune -f >/dev/null   # 이전 버전 이미지 정리 (디스크 확보)
  echo "[deploy] 성공: ${TAG}"
  exit 0
fi

echo "[deploy] 헬스체크 실패: ${TAG}" >&2
"${COMPOSE[@]}" logs --tail 50 server >&2 || true

if [ -n "$PREVIOUS" ] && [ "$PREVIOUS" != "$TAG" ]; then
  echo "[deploy] 직전 버전으로 롤백: ${PREVIOUS}" >&2
  up "$PREVIOUS"
  if wait_healthy; then
    echo "[deploy] 롤백 완료: ${PREVIOUS}" >&2
  else
    echo "[deploy] 롤백 후에도 헬스체크 실패. 서버에 접속해 확인하세요." >&2
  fi
fi
exit 1
