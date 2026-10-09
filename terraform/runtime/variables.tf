variable "db_snapshot_identifier" {
  description = "RDS snapshot identifier used to restore the database. Null creates a new database."
  type        = string
  default     = null
}
