#!/usr/bin/env bash

run_terraform_with_db_password() {
  local db_password="$1"
  shift

  TF_VAR_db_password="$db_password" terraform \
    -chdir="$RUNTIME_DIR" "$@"
}
