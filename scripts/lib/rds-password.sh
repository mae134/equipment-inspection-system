#!/usr/bin/env bash

modify_rds_master_password() {
  local db_password="$1"

  printf '%s' "$db_password" |
    jq -Rs \
      --arg identifier "$DB_INSTANCE_IDENTIFIER" \
      '{
        DBInstanceIdentifier: $identifier,
        MasterUserPassword: .,
        ApplyImmediately: true
      }' |
    aws_cli_with_secure_json \
      aws rds modify-db-instance \
      --profile "$AWS_PROFILE" \
      --region "$AWS_REGION" \
      --output json >/dev/null
}
