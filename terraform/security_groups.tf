resource "aws_security_group" "alb" {
  name        = "equipment-inspection-alb-sg"
  description = "Security group for equipment inspection ALB"
  vpc_id      = aws_vpc.main.id
}
