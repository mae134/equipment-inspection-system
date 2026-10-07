resource "aws_ssm_parameter" "db_url" {
  name = "/equipment-inspection/prod/db/url"
  type = "String"

  value = "jdbc:postgresql://${aws_db_instance.main.address}:${aws_db_instance.main.port}/${aws_db_instance.main.db_name}"
}

resource "aws_ssm_parameter" "db_username" {
  name  = "/equipment-inspection/prod/db/username"
  type  = "String"
  value = aws_db_instance.main.username
}

resource "aws_ssm_parameter" "db_password" {
  name        = "/equipment-inspection/prod/db/password"
  description = "RDS PostgreSQL password for equipment inspection application"
  type        = "SecureString"

  value_wo         = ephemeral.random_password.db.result
  value_wo_version = 2
}
