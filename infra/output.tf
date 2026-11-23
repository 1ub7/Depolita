output "public_ip" {
  description = ".env의 PUBLIC_HOST, 그리고 DOMAIN(DuckDNS 등)의 A 레코드에 넣을 값"
  value       = aws_eip.this.public_ip
}

output "ssh_command" {
  value = "ssh ubuntu@${aws_eip.this.public_ip}"
}
