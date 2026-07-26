#!/usr/bin/env bash

# 既存のGitHub Issueのタイトルを標準出力へ出力する
get_issue_title() {
  local issue_number="${1:-}"

  gh issue view "$issue_number" --json title --jq '.title'
}
