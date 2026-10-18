#!/usr/bin/env bash

modify_rds_master_password() {
  local db_password="$1"

  printf '{"DBInstanceIdentifier":"%s","MasterUserPassword":"%s","ApplyImmediately":true}\n' \
    "$DB_INSTANCE_IDENTIFIER" "$db_password" |
    aws rds modify-db-instance \
      --profile "$AWS_PROFILE" \
      --region "$AWS_REGION" \
      --cli-input-json file:///dev/stdin \
      --output json >/dev/null
}
