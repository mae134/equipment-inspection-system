variable "db_snapshot_identifier" {
  description = "RDS snapshot identifier used to restore the database. Null creates a new database."
  type        = string
  default     = null
}

variable "db_password_version" {
  description = "Version used to rotate the database password."
  type        = number
  default     = 1
}

variable "db_password" {
  description = "Initial database password for new RDS instances."
  type        = string
  sensitive   = true
  ephemeral   = true
  default     = null
}

variable "ecs_desired_count" {
  description = "Desired number of ECS tasks"
  type        = number
  default     = 0

  validation {
    condition     = var.ecs_desired_count >= 0
    error_message = "ecs_desired_count must be non-negative."
  }
}

variable "db_password_enabled" {
  description = "Whether to configure the RDS master password through Terraform."
  type        = bool
  default     = true
}
