resource "aws_sns_topic" "alerts" {
  name = "equipment-inspection-alerts"
}

data "aws_iam_policy_document" "sns_alerts" {

  statement {
    sid    = "__default_statement_ID"
    effect = "Allow"

    principals {
      type        = "AWS"
      identifiers = ["*"]
    }

    actions = [
      "SNS:GetTopicAttributes",
      "SNS:SetTopicAttributes",
      "SNS:AddPermission",
      "SNS:RemovePermission",
      "SNS:DeleteTopic",
      "SNS:Subscribe",
      "SNS:ListSubscriptionsByTopic",
      "SNS:Publish",
    ]

    resources = [
      aws_sns_topic.alerts.arn
    ]

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceOwner"
      values   = ["339741260090"]
    }
  }

  statement {
    sid    = "AllowBudgetPublish"
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["budgets.amazonaws.com"]
    }

    actions = [
      "sns:Publish"
    ]

    resources = [
      aws_sns_topic.alerts.arn
    ]
  }
}

resource "aws_sns_topic_policy" "alerts" {
  arn    = aws_sns_topic.alerts.arn
  policy = data.aws_iam_policy_document.sns_alerts.json
}
