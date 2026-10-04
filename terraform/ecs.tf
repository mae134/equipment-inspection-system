resource "aws_ecs_cluster" "main" {
  name = "equipment-inspection-cluster"

  configuration {
    execute_command_configuration {
      logging = "DEFAULT"
    }
  }
}

resource "aws_ecs_task_definition" "app" {
  family                   = "equipment-inspection-task"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn

  container_definitions = jsonencode([
    {
      name      = "equipment-inspection-app"
      image     = "339741260090.dkr.ecr.ap-northeast-1.amazonaws.com/equipment-inspection-app:latest"
      cpu       = 0
      essential = true

      environment      = []
      environmentFiles = []
      mountPoints      = []
      volumesFrom      = []
      ulimits          = []
      systemControls   = []

      portMappings = [
        {
          name          = "equipment-inspection-app-8080-tcp"
          containerPort = 8080
          hostPort      = 8080
          protocol      = "tcp"
          appProtocol   = "http"
        }
      ]

      secrets = [
        {
          name      = "SPRING_DATASOURCE_URL"
          valueFrom = "/equipment-inspection/prod/db/url"
        },
        {
          name      = "SPRING_DATASOURCE_USERNAME"
          valueFrom = "/equipment-inspection/prod/db/username"
        },
        {
          name      = "SPRING_DATASOURCE_PASSWORD"
          valueFrom = "/equipment-inspection/prod/db/password"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"

        options = {
          "awslogs-group"         = "/ecs/equipment-inspection-task"
          "awslogs-region"        = "ap-northeast-1"
          "awslogs-stream-prefix" = "ecs"
        }

        secretOptions = []
      }
    }
  ])

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }
}
