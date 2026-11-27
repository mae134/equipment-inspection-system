#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
test_dir="$(mktemp -d)"
trap 'rm -rf "$test_dir"' EXIT

mkdir -p "$test_dir"/{repo/scripts/user,repo/target,bin,outside/config}
launcher="$test_dir/repo/scripts/user/create-user.sh"
jar="$test_dir/repo/target/equipment-inspection-system-0.0.1-SNAPSHOT.jar"
cp "$script_dir/../../user/create-user.sh" "$launcher"
ln -s "$(type -P dirname)" "$test_dir/bin/dirname"
ln -s "$(type -P bash)" "$test_dir/bin/bash"
cd "$test_dir/outside"

assert_run() {
  local expected_status="$1" expected_output="$2" status=0
  shift 2
  output="$("$@" 2>&1)" || status=$?
  if [[ $status != "$expected_status" || $output != *"$expected_output"* ]]; then
    printf 'FAIL: expected exit %s and message containing "%s", got exit %s\n' \
      "$expected_status" "$expected_output" "$status" >&2
    exit 1
  fi
}

assert_run 0 'Usage:' "$launcher" --help
assert_run 2 'Usage:' "$launcher"
assert_run 2 'Usage:' "$launcher" --unknown
assert_run 2 'Usage:' "$launcher" --local --aws
assert_run 2 'Usage:' "$launcher" --local extra
assert_run 2 'Usage:' "$launcher" --help extra
assert_run 1 'not implemented in Phase 3' env "PATH=$test_dir/bin" "$launcher" --aws
assert_run 1 'Java is required' env "PATH=$test_dir/bin" "$launcher" --local

cat > "$test_dir/bin/java" <<'MOCK'
#!/bin/bash
set -euo pipefail
[[ -t 0 && -t 1 && -t 2 && ! -e /proc/self/fd/3 ]]
[[ $# == 3 && $1 == -jar && $3 == --user-registration=local ]]
[[ $2 == "${PWD%/*}/repo/target/equipment-inspection-system-0.0.1-SNAPSHOT.jar" ]]
[[ $DB_URL == jdbc:postgresql://127.0.0.1:5433/equipment_inspection ]]
[[ $DB_USERNAME == shell_test_user && $DB_PASSWORD == shell-test-sentinel ]]
[[ $SPRING_CONFIG_LOCATION == classpath:/ ]]
[[ ! -v SPRING_DATASOURCE_URL && ! -v SPRING_APPLICATION_JSON && ! -v JAVA_TOOL_OPTIONS ]]
[[ ! -v 'spring.datasource.hikari.jdbc-url' ]]
printf 'Mock Java invoked\n'
exit 7
MOCK
chmod +x "$test_dir/bin/java"

assert_run 1 './mvnw package' env "PATH=$test_dir/bin" "$launcher" --local
touch "$jar"

for url in '' \
  'jdbc:postgresql://example.invalid:5433/data' \
  'jdbc:postgresql://localhost:5433,example.invalid:5433/data' \
  'jdbc:postgresql://localhost:5433/data?password=sentinel' \
  'jdbc:postgresql://localhost/data' \
  'jdbc:postgresql://localhost:65536/data'; do
  assert_run 1 'DB_URL' env "PATH=$test_dir/bin" "DB_URL=$url" "$launcher" --local
  [[ $output != *sentinel* ]]
done

export PATH="$test_dir/bin:$PATH"
export DB_URL='jdbc:postgresql://localhost:5433/equipment_inspection'
assert_run 1 'Set DB_PASSWORD' env -u DB_PASSWORD "$launcher" --local
export DB_USERNAME=shell_test_user DB_PASSWORD=shell-test-sentinel
assert_run 1 'interactive terminal' "$launcher" --local

# External settings are present during launch but must stay outside the Java process.
printf '%s\n' 'spring.datasource.hikari.jdbc-url=jdbc:postgresql://example.invalid/remote' \
  > config/application.properties
printf -v command '%q --local' "$launcher"
assert_run 7 'Mock Java invoked' env \
  SPRING_DATASOURCE_URL=jdbc:postgresql://example.invalid/remote \
  SPRING_APPLICATION_JSON='{"spring":{"datasource":{"url":"remote"}}}' \
  SPRING_CONFIG_LOCATION="$PWD/config/" \
  JAVA_TOOL_OPTIONS=-Dspring.datasource.url=remote \
  spring.datasource.hikari.jdbc-url=remote \
  script -qec "$command" /dev/null
[[ $output == *'Local PostgreSQL target: 127.0.0.1:5433/equipment_inspection'* ]]
[[ $output != *shell-test-sentinel* ]]

echo 'PASS: create-user argument handling, local safety checks, and Java handoff'
