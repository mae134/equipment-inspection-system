resource "aws_ecs_cluster" "main" {
  name = "equipment-inspection-cluster"

  configuration {
    execute_command_configuration {
      logging = "DEFAULT"
    }
  }
}
