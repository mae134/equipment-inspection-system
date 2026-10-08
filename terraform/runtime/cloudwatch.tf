resource "aws_cloudwatch_log_group" "ecs" {
  name              = "/ecs/equipment-inspection-task"
  retention_in_days = 7
}

resource "aws_cloudwatch_metric_alarm" "rds_cpu_high" {
  alarm_name        = "equipment-inspection-rds-cpu-high"
  alarm_description = "RDS PostgreSQL のCPU使用率が80%を3分間連続で超えた場合に通知する。"

  namespace   = "AWS/RDS"
  metric_name = "CPUUtilization"
  statistic   = "Average"

  period              = 60
  evaluation_periods  = 3
  datapoints_to_alarm = 3

  threshold           = 80
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    DBInstanceIdentifier = aws_db_instance.main.identifier
  }

  alarm_actions = [
    aws_sns_topic.alerts.arn
  ]
}
