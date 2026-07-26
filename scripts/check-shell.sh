#!/usr/bin/env bash

set -euo pipefail

if ! command -v shellcheck >/dev/null 2>&1; then
  echo "Error: ShellCheck is not installed."
  echo
  echo "Install:"
  echo "  sudo apt install shellcheck"
  exit 1
fi

shellcheck \
  scripts/*.sh \
  scripts/lib/*.sh

echo
echo "✓ ShellCheck passed."
