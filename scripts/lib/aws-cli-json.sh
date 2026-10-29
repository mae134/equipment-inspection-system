#!/usr/bin/env bash

aws_cli_with_secure_json() (
  local tmp_file
  tmp_file="$(mktemp /dev/shm/aws-cli-json.XXXXXX)" || exit 1

  trap 'rm -f -- "$tmp_file"' EXIT

  cat > "$tmp_file" || exit 1

  "$@" --cli-input-json "file://$tmp_file"
)
