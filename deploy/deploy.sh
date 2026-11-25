#!/usr/bin/env bash
# EC2 에서 GHCR 이미지로 Depolita 를 배포한다.
# - 자동: GitHub Actions(cd.yml) → SSM 문서 depolita-deploy 가 이 스크립트를 실행
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
  # web 은 depends_on(server: service_healthy) 이라, 새 server 가 끝내 healthy 가 안 되면
  # 이 명령 자체가 실패한다 → 호출하는 쪽에서 if 로 감싸 set -e 로 스크립트가 끝나지 않게 한다
  IMAGE_TAG="$1" "${COMPOSE[@]}" up -d --no-build
}

web_healthy() {
  local id
  id="$("${COMPOSE[@]}" ps -q web 2>/dev/null || true)"
  [ -n "$id" ] && [ "$(docker inspect -f '{{.State.Health.Status}}' "$id" 2>/dev/null)" = "healthy" ]
}

wait_healthy() {
  # Spring Boot 기동 + Flyway 마이그레이션 + web 헬스체크(30초 주기)까지 최대 약 3분 기다린다
  for _ in $(seq 1 36); do
    if curl -fsS "$HEALTH_URL" >/dev/null 2>&1 && web_healthy; then
      return 0
    fi
    sleep 5
  done
  return 1
}

# 새 버전을 올리고 server·web 이 모두 healthy 가 되면 성공
deploy_version() {
  up "$1" && wait_healthy
}

cleanup_old_images() {
  # 커밋 SHA 태그가 붙은 이미지는 dangling 이 아니라서 image prune 으로 지워지지 않는다.
  # 지금 버전과 직전 버전(빠른 롤백용)만 남기고 지운다.
  docker images --format '{{.Repository}}:{{.Tag}}' \
    | grep -E '/depolita-(server|web):[0-9a-f]{40}$' \
    | grep -v -e ":${TAG}\$" ${PREVIOUS:+-e ":${PREVIOUS}\$"} \
    | xargs -r docker rmi >/dev/null 2>&1 || true
  docker image prune -f >/dev/null || true
}

echo "[deploy] ${PREVIOUS:-없음} → ${TAG}"

if deploy_version "$TAG"; then
  echo "$TAG" > "$STATE_FILE"
  cleanup_old_images
  echo "[deploy] 성공: ${TAG}"
  exit 0
fi

echo "[deploy] 헬스체크 실패: ${TAG}" >&2
"${COMPOSE[@]}" logs --tail 50 server web >&2 || true

if [ -n "$PREVIOUS" ] && [ "$PREVIOUS" != "$TAG" ]; then
  echo "[deploy] 직전 버전으로 롤백: ${PREVIOUS}" >&2
  if deploy_version "$PREVIOUS"; then
    echo "[deploy] 롤백 완료: ${PREVIOUS}" >&2
  else
    echo "[deploy] 롤백 후에도 헬스체크 실패. 서버에 접속해 확인하세요." >&2
  fi
else
  echo "[deploy] 되돌릴 직전 버전 기록이 없습니다 (${STATE_FILE})." >&2
fi
exit 1
