data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"]

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }
}

resource "aws_key_pair" "this" {
  key_name   = "depolita-key"
  public_key = file(pathexpand(var.public_key_path))
}

resource "aws_security_group" "this" {
  name        = "depolita-sg"
  description = "Depolita server"

  ingress {
    description = "SSH (my IP only)"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.ssh_allowed_cidr]
  }

  # 80번은 dashboard_allowed_cidrs 로 대시보드를 잠가도 항상 열어둔다.
  # Let's Encrypt HTTP-01 인증과 http→https 리다이렉트에만 쓰이고, 실제 대시보드는 443 에서만 응답한다.
  ingress {
    description = "HTTP for ACME challenge and HTTPS redirect (always open)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "Dashboard (https)"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = var.dashboard_allowed_cidrs
  }

  ingress {
    description = "Deployed apps"
    from_port   = var.app_port_from
    to_port     = var.app_port_to
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_instance" "this" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.instance_type
  key_name               = aws_key_pair.this.key_name
  vpc_security_group_ids = [aws_security_group.this.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2.name

  user_data = templatefile("${path.module}/user_data.sh", {
    swap_size_gb = var.swap_size_gb
    repo_url     = var.repo_url
  })

  root_block_device {
    volume_type = "gp3"
    volume_size = var.disk_size_gb
  }

  # IMDSv2 강제 + 토큰 응답이 한 홉만 가도록 제한한다.
  # Docker 컨테이너는 브리지를 거쳐 한 홉이 더 늘어나므로, 배포된 앱이 EC2 메타데이터
  # (나중에 붙일 IAM 역할 자격증명 포함)를 읽을 수 없다.
  metadata_options {
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }

  tags = {
    Name = "depolita"
  }

  lifecycle {
    ignore_changes = [ami, user_data]
  }
}

resource "aws_eip" "this" {
  instance = aws_instance.this.id
  domain   = "vpc"

  tags = {
    Name = "depolita"
  }
}
