variable "region" {
  description = "AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "instance_type" {
  description = "EC2 인스턴스 타입"
  type        = string
  default     = "t3.small"
}

variable "my_ip_cidr" {
  description = "SSH·대시보드 접속을 허용할 내 IP"
  type        = string
  validation {
    condition     = can(cidrhost(var.my_ip_cidr, 0))
    error_message = "CIDR 형식이어야 합니다."
  }
}

variable "public_key_path" {
  description = "EC2에 등록할 SSH 공개키 경로"
  type        = string
  default     = "~/.ssh/id_ed25519.pub"
}

variable "disk_size_gb" {
  description = "루트 디스크 크기(GB). 도커 이미지가 쌓이므로 너무 작게 잡지 않는다"
  type        = number
  default     = 30
}

variable "swap_size_gb" {
  description = "스왑 크기(GB). 빌드 중 메모리 부족(OOM) 방지용"
  type        = number
  default     = 4
}

variable "app_port_from" {
  description = "배포 앱에 할당하는 포트 범위 시작 (PortAllocator.MIN_PORT 와 맞춘다)"
  type        = number
  default     = 10001
}

variable "app_port_to" {
  description = "배포 앱에 할당하는 포트 범위 끝 (PortAllocator.MAX_PORT 와 맞춘다)"
  type        = number
  default     = 10999
}

variable "repo_url" {
  description = "서버에 클론할 Depolita 저장소"
  type        = string
  default     = "https://github.com/1ub7/Depolita.git"
}
