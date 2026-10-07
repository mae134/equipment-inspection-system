#!/usr/bin/env bash

# プロジェクト固有の環境変数を .env から読み込む
set -a
source "$(dirname "$0")/../.env"
set +a

# SNS Subscription の登録に使用するAWSリソースとCLI設定
SNS_TOPIC_ARN="arn:aws:sns:ap-northeast-1:339741260090:equipment-inspection-alerts"
AWS_PROFILE="terraform-execution"
AWS_REGION="ap-northeast-1"

# 通知先メールアドレスが設定されていない場合は処理を中止する
if [[ -z "${ALERT_EMAIL:-}" ]]; then
  echo "ERROR: ALERT_EMAIL is not set in .env." >&2
  exit 1
fi

# 同じメールアドレスが既にSNS Topicを購読しているか確認する
EXISTING_SUBSCRIPTION_ARN=$(
  aws sns list-subscriptions-by-topic \
    --topic-arn "$SNS_TOPIC_ARN" \
    --profile "$AWS_PROFILE" \
    --region "$AWS_REGION" \
    --query "Subscriptions[?Protocol=='email' && Endpoint=='${ALERT_EMAIL}'].SubscriptionArn | [0]" \
    --output text
)

# 既に購読済みの場合は重複登録せず終了する
if [[ "$EXISTING_SUBSCRIPTION_ARN" != "None" ]]; then
  echo "SNS email subscription already exists."
  exit 0
fi

# SNS Topicにメールアドレスを購読登録する
# 登録後、AWSから届く確認メールで承認すると通知が有効になる
aws sns subscribe \
  --topic-arn "$SNS_TOPIC_ARN" \
  --protocol email \
  --notification-endpoint "$ALERT_EMAIL" \
  --profile "$AWS_PROFILE" \
  --region "$AWS_REGION"
