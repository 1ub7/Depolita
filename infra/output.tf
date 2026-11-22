output "public_ip" {
  description = ".env의 PUBLIC_HOST에 넣을 값"
  value       = aws_eip.this.public_ip
}

output "ssh_command" {
  value = "ssh ubuntu@${aws_eip.this.public_ip}"
}

output "dashboard_url" {
  value = "http://${aws_eip.this.public_ip}"
}
