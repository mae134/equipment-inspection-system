#!/usr/bin/env bash

set +x
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: create-user.sh --local | --aws | --help

  --local  Register a user on a local PostgreSQL server from an interactive terminal.
           Export DB_URL (for example jdbc:postgresql://localhost:5433/equipment_inspection),
           DB_PASSWORD, and optionally DB_USERNAME (defaults to equipment_user).
           Use a direct local database listener. A forwarded port can lead to a remote database.
           The launcher uses packaged Spring configuration and these DB variables only.
  --aws    AWS registration is unavailable in Phase 3.
  --help   Show this help.
USAGE
}

fail() {
  printf 'Error: %s\n' "$1" >&2
  exit 1
}

case "$#:${1:-}" in
  1:--help) usage; exit 0 ;;
  1:--aws) fail 'AWS registration is not implemented in Phase 3.' ;;
  1:--local) ;;
  *) usage >&2; exit 2 ;;
esac

repo_root="$(CDPATH='' cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)"
jar="$repo_root/target/equipment-inspection-system-0.0.1-SNAPSHOT.jar"
java="$(type -P java)" || fail 'Java is required. Install Java 21 or later and add it to PATH.'
[[ -x "$java" ]] || fail 'Java is not executable.'
[[ -f "$jar" && -r "$jar" ]] || fail 'Application JAR missing or unreadable. Run ./mvnw package from the repository root.'

# Restrict the JDBC URL to one literal host, an explicit port, and a plain database name.
local_url_pattern='^jdbc:postgresql://(localhost|127\.0\.0\.1):([1-9][0-9]{0,4})/([a-zA-Z0-9_][a-zA-Z0-9_-]{0,62})$'
[[ ${DB_URL:-} =~ $local_url_pattern ]] ||
  fail 'Set DB_URL to a local PostgreSQL URL with an explicit port, a database name, and no JDBC options.'
port="${BASH_REMATCH[2]}"
database="${BASH_REMATCH[3]}"
((port <= 65535)) || fail 'DB_URL must use a port from 1 through 65535.'
[[ -n ${DB_PASSWORD:-} ]] || fail 'Set DB_PASSWORD for the local PostgreSQL server.'
[[ -t 0 && -t 1 && -t 2 ]] || fail 'Local registration requires an interactive terminal.'

printf 'Local PostgreSQL target: 127.0.0.1:%s/%s\n' "$port" "$database"

# Start with an empty environment so Spring/JVM overrides cannot replace the validated
# destination. Pass credentials over a private descriptor to keep them out of argv.
# shellcheck disable=SC2016
exec -c /bin/bash -c '
  set -euo pipefail
  IFS= read -r -d "" DB_USERNAME <&3
  IFS= read -r -d "" DB_PASSWORD <&3
  exec 3<&-
  export DB_USERNAME DB_PASSWORD DB_URL="$1"
  export LANG=C.UTF-8 SPRING_CONFIG_LOCATION=classpath:/
  shift
  exec "$@"
' create-user "jdbc:postgresql://127.0.0.1:$port/$database" \
  "$java" -jar "$jar" --user-registration=local \
  3< <(printf '%s\0' "${DB_USERNAME:-equipment_user}" "$DB_PASSWORD")
