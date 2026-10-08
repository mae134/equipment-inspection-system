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

resource "aws_ecs_service" "app" {
  name                  = "equipment-inspection-service"
  cluster               = aws_ecs_cluster.main.id
  task_definition       = "${aws_ecs_task_definition.app.family}:${aws_ecs_task_definition.app.revision}"
  desired_count         = 0
  wait_for_steady_state = false

  capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = 1
    base              = 0
  }

  network_configuration {
    subnets = [
      aws_subnet.public_a.id,
      aws_subnet.public_c.id
    ]

    security_groups = [
      aws_security_group.ecs.id
    ]

    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = "equipment-inspection-app"
    container_port   = 8080
  }

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  health_check_grace_period_seconds = 0
  enable_ecs_managed_tags           = true
  enable_execute_command            = false
  propagate_tags                    = "NONE"
  availability_zone_rebalancing     = "ENABLED"
}
