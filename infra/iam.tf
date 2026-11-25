# ─────────────────────────────────────────────────────────────
# GitHub Actions → AWS: OIDC 로 단기 자격증명을 받는다 (장기 Access Key 없음)
# ─────────────────────────────────────────────────────────────

# 한 AWS 계정에 같은 URL 의 OIDC 공급자는 하나만 만들 수 있다.
# 이미 있다면 create_github_oidc_provider = false 로 두고 기존 것을 쓴다.
resource "aws_iam_openid_connect_provider" "github" {
  count = var.create_github_oidc_provider ? 1 : 0

  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
}

data "aws_iam_openid_connect_provider" "github" {
  count = var.create_github_oidc_provider ? 0 : 1

  url = "https://token.actions.githubusercontent.com"
}

locals {
  github_oidc_provider_arn = var.create_github_oidc_provider ? aws_iam_openid_connect_provider.github[0].arn : data.aws_iam_openid_connect_provider.github[0].arn
}

data "aws_iam_policy_document" "github_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.github_oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # 이 저장소의 main 브랜치에서 실행된 워크플로만 역할을 받을 수 있다 (PR·포크 불가)
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repository}:ref:refs/heads/main"]
    }
  }
}

resource "aws_iam_role" "github_deploy" {
  name                 = "depolita-github-deploy"
  description          = "GitHub Actions deploy role (SSM depolita-deploy document only)"
  assume_role_policy   = data.aws_iam_policy_document.github_assume.json
  max_session_duration = 3600
}

# 배포 역할은 "이 인스턴스에서 depolita-deploy 문서 실행"만 할 수 있다.
# AWS-RunShellScript 같은 임의 명령 실행 문서는 허용하지 않는다.
data "aws_iam_policy_document" "github_deploy" {
  statement {
    sid       = "RunDeployDocumentOnDepolita"
    actions   = ["ssm:SendCommand"]
    resources = [
      aws_instance.this.arn,
      aws_ssm_document.deploy.arn,
    ]
  }

  statement {
    sid     = "ReadCommandResult"
    actions = [
      "ssm:GetCommandInvocation",
      "ssm:ListCommandInvocations",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "github_deploy" {
  name   = "depolita-deploy"
  role   = aws_iam_role.github_deploy.id
  policy = data.aws_iam_policy_document.github_deploy.json
}

# ─────────────────────────────────────────────────────────────
# EC2 → SSM: Run Command 를 받기 위한 최소 권한
# ─────────────────────────────────────────────────────────────

data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ec2" {
  name               = "depolita-ec2"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

resource "aws_iam_role_policy_attachment" "ec2_ssm" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "ec2" {
  name = "depolita-ec2"
  role = aws_iam_role.ec2.name
}
