resource "aws_cloudwatch_log_group" "ecs" {
  name              = "/ecs/equipment-inspection-task"
  retention_in_days = 7
}
