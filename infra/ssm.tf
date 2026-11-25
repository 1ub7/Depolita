# GitHub Actions 가 실행하는 배포 명령.
# 커밋 SHA 만 인자로 받으며(형식 검증), 그 커밋을 체크아웃한 뒤 deploy/deploy.sh 를 실행한다.
# 롤백도 같은 문서에 이전 커밋 SHA 를 넣어 실행한다.
resource "aws_ssm_document" "deploy" {
  name            = "depolita-deploy"
  document_type   = "Command"
  document_format = "JSON"

  content = jsonencode({
    schemaVersion = "2.2"
    description   = "Deploy Depolita images tagged with the given commit SHA"
    parameters    = {
      tag = {
        type           = "String"
        description    = "Commit SHA to deploy (40 hex chars)"
        allowedPattern = "^[0-9a-f]{40}$"
      }
    }
    mainSteps = [
      {
        action = "aws:runShellScript"
        name   = "deploy"
        inputs = {
          timeoutSeconds   = "900"
          workingDirectory = "/home/ubuntu/Depolita"
          runCommand       = [
            "set -e",
            "sudo -u ubuntu git fetch --depth 1 origin {{ tag }}",
            "sudo -u ubuntu git checkout --force --detach FETCH_HEAD",
            "bash deploy/deploy.sh {{ tag }}",
          ]
        }
      }
    ]
  })
}
