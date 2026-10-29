provider "aws" {
  region  = "ap-northeast-1"
  profile = "terraform-admin"

  assume_role {
    role_arn = "arn:aws:iam::339741260090:role/terraform-execution-role"
  }
}
