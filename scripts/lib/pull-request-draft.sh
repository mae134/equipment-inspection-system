#!/usr/bin/env bash

# Title見出し直後の最初の非空行を取得する
extract_pull_request_title() {
  local input_file=()

  if [[ $# -gt 0 ]]; then
    input_file=("$1")
  fi

  awk '
    $0 == "# Title" { in_title = 1; next }
    in_title && /^# / { exit }
    in_title && $0 !~ /^[[:space:]]*$/ { print; exit }
  ' "${input_file[@]}"
}

# Title見出しとタイトル行を除いた残りの内容をPull Request本文として取得する
extract_pull_request_body() {
  local input_file=()

  if [[ $# -gt 0 ]]; then
    input_file=("$1")
  fi

  awk '
    title_read { print }
    in_title && $0 !~ /^[[:space:]]*$/ { title_read = 1; next }
    $0 == "# Title" { in_title = 1; next }
  ' "${input_file[@]}"
}

# 空白文字以外を含む場合に成功を返す
has_non_whitespace_content() {
  local content="$1"

  [[ "$content" =~ [^[:space:]] ]]
}

# Prompt Templateの出力例から見出し構造を取得する
extract_pull_request_template_headings() {
  local template_file="$1"

  awk '
    found_format && /^#{1,6} / { print }
    $0 == "Generate the Pull Request using the Markdown template below." { found_format = 1 }
  ' "$template_file"
}

# 生成結果がPrompt Template由来の見出し構造と内容を満たすことを確認する
validate_pull_request_output() {
  local template_file="$1"
  local generated_output="$2"
  local expected_index=0
  local generated_heading
  local expected_heading
  local first_non_empty_line
  local -a template_headings=()
  local -a generated_headings=()

  mapfile -t template_headings < <(extract_pull_request_template_headings "$template_file")
  mapfile -t generated_headings < <(printf '%s\n' "$generated_output" | awk '/^#{1,6} / { print }')

  [[ ${#template_headings[@]} -gt 0 ]] || return 1
  [[ ${#generated_headings[@]} -gt 0 ]] || return 1
  [[ "$generated_output" != *'{{'* ]] || return 1
  first_non_empty_line="$(printf '%s\n' "$generated_output" | awk '$0 !~ /^[[:space:]]*$/ { print; exit }')"
  [[ "$first_non_empty_line" == "${template_headings[0]}" ]] || return 1

  for generated_heading in "${generated_headings[@]}"; do
    while ((expected_index < ${#template_headings[@]})); do
      expected_heading="${template_headings[$expected_index]}"
      if [[ "$expected_heading" == *"(Optional)"* && "$generated_heading" != "$expected_heading" ]]; then
        expected_index=$((expected_index + 1))
        continue
      fi
      break
    done

    ((expected_index < ${#template_headings[@]})) || return 1
    [[ "$generated_heading" == "${template_headings[$expected_index]}" ]] || return 1
    expected_index=$((expected_index + 1))
  done

  while ((expected_index < ${#template_headings[@]})); do
    [[ "${template_headings[$expected_index]}" == *"(Optional)"* ]] || return 1
    expected_index=$((expected_index + 1))
  done

  printf '%s\n' "$generated_output" | awk '
    /^#{1,6} / {
      if (section_seen && !section_has_content) {
        exit 1
      }
      section_seen = 1
      section_has_content = 0
      next
    }
    section_seen && $0 !~ /^[[:space:]]*$/ {
      section_has_content = 1
    }
    END {
      if (!section_seen || !section_has_content) {
        exit 1
      }
    }
  '
}
