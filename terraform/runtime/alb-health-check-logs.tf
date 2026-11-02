data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "alb_health_check_logs" {
  bucket_prefix = "equipment-inspection-alb-health-"
  force_destroy = true
}

resource "aws_s3_bucket_public_access_block" "alb_health_check_logs" {
  bucket = aws_s3_bucket.alb_health_check_logs.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "alb_health_check_logs" {
  bucket = aws_s3_bucket.alb_health_check_logs.id

  rule {
    id     = "expire-health-check-logs"
    status = "Enabled"

    filter {
      prefix = ""
    }

    expiration {
      days = 7
    }
  }
}

data "aws_iam_policy_document" "alb_health_check_logs" {
  statement {
    sid    = "AllowALBHealthCheckLogDelivery"
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["logdelivery.elasticloadbalancing.amazonaws.com"]
    }

    actions = ["s3:PutObject"]

    resources = [
      "${aws_s3_bucket.alb_health_check_logs.arn}/health-check/AWSLogs/${data.aws_caller_identity.current.account_id}/*"
    ]
  }
}

resource "aws_s3_bucket_policy" "alb_health_check_logs" {
  bucket = aws_s3_bucket.alb_health_check_logs.id
  policy = data.aws_iam_policy_document.alb_health_check_logs.json
}
