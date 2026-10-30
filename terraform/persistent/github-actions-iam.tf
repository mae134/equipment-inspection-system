data "aws_iam_policy_document" "github_actions_assume_role" {
  statement {
    effect = "Allow"

    actions = [
      "sts:AssumeRoleWithWebIdentity"
    ]

    principals {
      type = "Federated"

      identifiers = [
        aws_iam_openid_connect_provider.github_actions.arn
      ]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"

      values = [
        "sts.amazonaws.com"
      ]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"

      values = [
        "repo:mae134@166838175/equipment-inspection-system@1311684664:ref:refs/heads/main"
      ]
    }
  }
}

resource "aws_iam_role" "github_actions_deploy" {
  name = "equipment-inspection-github-actions-deploy-role"

  assume_role_policy = data.aws_iam_policy_document.github_actions_assume_role.json
}

data "aws_iam_policy_document" "github_actions_deploy" {
  statement {
    sid       = "GetEcrAuthorizationToken"
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid    = "PushEcrImages"
    effect = "Allow"

    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:CompleteLayerUpload",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart"
    ]

    resources = [
      "arn:aws:ecr:ap-northeast-1:339741260090:repository/equipment-inspection-app"
    ]
  }

  statement {
    sid    = "DescribeEcsResources"
    effect = "Allow"

    actions = [
      "ecs:DescribeServices",
      "ecs:DescribeTaskDefinition"
    ]

    resources = ["*"]
  }

  statement {
    sid     = "RegisterTaskDefinition"
    effect  = "Allow"
    actions = ["ecs:RegisterTaskDefinition"]

    resources = [
      "arn:aws:ecs:ap-northeast-1:339741260090:task-definition/equipment-inspection-task:*"
    ]
  }

  statement {
    sid     = "UpdateEcsService"
    effect  = "Allow"
    actions = ["ecs:UpdateService"]

    resources = [
      "arn:aws:ecs:ap-northeast-1:339741260090:service/equipment-inspection-cluster/equipment-inspection-service"
    ]
  }

  statement {
    sid     = "PassEcsExecutionRole"
    effect  = "Allow"
    actions = ["iam:PassRole"]

    resources = [
      "arn:aws:iam::339741260090:role/equipment-inspection-ecs-task-execution-role"
    ]

    condition {
      test     = "StringEquals"
      variable = "iam:PassedToService"
      values   = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "github_actions_deploy" {
  name   = "equipment-inspection-github-actions-deploy-policy"
  role   = aws_iam_role.github_actions_deploy.id
  policy = data.aws_iam_policy_document.github_actions_deploy.json
}
