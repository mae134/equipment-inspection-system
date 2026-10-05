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
