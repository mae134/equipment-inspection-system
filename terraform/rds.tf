resource "aws_db_subnet_group" "main" {
  name        = "equipment-inspection-db-subnet-group"
  description = "DB subnet group for equipment inspection system"

  subnet_ids = [
    aws_subnet.private_a.id,
    aws_subnet.private_c.id
  ]
}

resource "aws_db_instance" "main" {
  identifier = "equipment-inspection-db"

  engine         = "postgres"
  engine_version = "18.3"
  instance_class = "db.t4g.micro"

  allocated_storage     = 20
  max_allocated_storage = 1000
  storage_type          = "gp2"
  storage_encrypted     = true

  db_name  = "equipment_inspection"
  username = "postgres"
  port     = 5432

  db_subnet_group_name = aws_db_subnet_group.main.name

  vpc_security_group_ids = [
    aws_security_group.rds.id
  ]

  publicly_accessible = false
  multi_az            = false

  backup_retention_period = 1
  backup_window           = "17:34-18:04"
  maintenance_window      = "sat:19:42-sat:20:12"

  copy_tags_to_snapshot = true
  deletion_protection   = false

  auto_minor_version_upgrade = true

  performance_insights_enabled = true
  monitoring_interval          = 0

  iam_database_authentication_enabled = false

  ca_cert_identifier = "rds-ca-rsa2048-g1"

  skip_final_snapshot = true
}
