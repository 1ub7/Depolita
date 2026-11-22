#!/bin/bash
# EC2 최초 부팅 시 1회 실행된다. 로그: /var/log/cloud-init-output.log
set -euxo pipefail

# 1) 스왑: 작은 인스턴스에서 Gradle/도커 빌드 중 OOM 방지
if [ ! -f /swapfile ]; then
  fallocate -l ${swap_size_gb}G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# 2) Docker Engine + Compose 플러그인
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker
usermod -aG docker ubuntu

# 3) 저장소 클론 (시크릿은 여기 넣지 않는다. .env 는 SSH 로 접속해 직접 작성)
if [ ! -d /home/ubuntu/Depolita ]; then
  sudo -u ubuntu git clone ${repo_url} /home/ubuntu/Depolita
fi

touch /home/ubuntu/.bootstrap-done
