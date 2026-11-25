output "public_ip" {
  description = ".env의 PUBLIC_HOST, 그리고 DOMAIN(DuckDNS 등)의 A 레코드에 넣을 값"
  value       = aws_eip.this.public_ip
}

output "ssh_command" {
  value = "ssh ubuntu@${aws_eip.this.public_ip}"
}

output "instance_id" {
  description = "GitHub 저장소 Variables 의 EC2_INSTANCE_ID 에 넣을 값"
  value       = aws_instance.this.id
}

output "deploy_role_arn" {
  description = "GitHub 저장소 Variables 의 AWS_DEPLOY_ROLE_ARN 에 넣을 값"
  value       = aws_iam_role.github_deploy.arn
}

output "deploy_document_name" {
  value = aws_ssm_document.deploy.name
}
