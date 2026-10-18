#!/usr/bin/env bash

put_db_password_to_ssm() {
  local db_password="$1"

  printf '%s' "$db_password" |
    jq -Rs '{
      Name: "/equipment-inspection/prod/db/password",
      Type: "SecureString",
      Value: .,
      Overwrite: true
    }' |
    aws ssm put-parameter \
      --profile "$AWS_PROFILE" \
      --region "$AWS_REGION" \
      --cli-input-json file:///dev/stdin \
      --output json >/dev/null
}
