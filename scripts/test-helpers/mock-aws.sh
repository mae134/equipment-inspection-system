#!/usr/bin/env bash
set -euo pipefail

MOCK_RDS_STATUS="${MOCK_RDS_STATUS:-unexpected}"
MOCK_SNAPSHOT_RESULT="${MOCK_SNAPSHOT_RESULT:-success}"
MOCK_SKIP_SNAPSHOT="${MOCK_SKIP_SNAPSHOT:-false}"

case "${1:-} ${2:-}" in
"sts get-caller-identity")
  printf '%s\n' \
    "${MOCK_CALLER_IDENTITY:-{\"Account\":\"339741260090\",\"Arn\":\"arn:aws:sts::339741260090:assumed-role/terraform-execution-role/test\"}}"
  ;;

"rds describe-db-snapshots")
  SNAPSHOT_STATUS="${MOCK_RESTORE_SNAPSHOT_STATUS:-available}"
  SOURCE_DB="${MOCK_RESTORE_SOURCE_DB:-equipment-inspection-db}"

  jq -n \
    --arg status "$SNAPSHOT_STATUS" \
    --arg source "$SOURCE_DB" \
    '{
      DBSnapshots: [
        {
          Status: $status,
          DBInstanceIdentifier: $source
        }
      ]
    }'

  echo "SNAPSHOT_DESCRIBED" >>"$MOCK_LOG"
  ;;

"rds describe-db-instances")
  case "$MOCK_RDS_STATUS" in
  available)
    echo "available"
    echo "RDS_DESCRIBED" >>"$MOCK_LOG"
    ;;
  stopped)
    echo "stopped"
    echo "RDS_STOPPED" >>"$MOCK_LOG"
    ;;
  not-found)
    echo "DBInstanceNotFound: DB instance does not exist" >&2
    echo "RDS_NOT_FOUND" >>"$MOCK_LOG"
    exit 255
    ;;
  *)
    echo "Unexpected RDS status: $MOCK_RDS_STATUS" >&2
    exit 1
    ;;
  esac
  ;;

"rds start-db-instance")

  if [[ "$MOCK_SKIP_SNAPSHOT" == "true" ]]; then
    echo "UNEXPECTED_RDS_OPERATION" >>"$MOCK_LOG"
    exit 1
  fi

  if [[ "$MOCK_RDS_STATUS" != "stopped" ]]; then
    echo "Unexpected RDS start" >&2
    exit 1
  fi

  echo "RDS_STARTED" >>"$MOCK_LOG"
  ;;

"rds create-db-snapshot")

  if [[ "$MOCK_SKIP_SNAPSHOT" == "true" ]]; then
    echo "UNEXPECTED_RDS_OPERATION" >>"$MOCK_LOG"
    exit 1
  fi

  if [[ "$MOCK_RDS_STATUS" != "available" && "$MOCK_RDS_STATUS" != "stopped" ]]; then
    echo "Unexpected snapshot creation" >&2
    exit 1
  fi

  if [[ "$MOCK_RDS_STATUS" == "stopped" ]]; then
    if ! grep -qx "RDS_AVAILABLE" "$MOCK_LOG"; then
      echo "Snapshot attempted before RDS became available" >&2
      exit 1
    fi
  fi

  if [[ "$MOCK_SNAPSHOT_RESULT" == "failure" ]]; then
    echo "SNAPSHOT_CREATE_FAILED" >>"$MOCK_LOG"
    echo "Simulated snapshot creation failure" >&2
    exit 1
  fi

  echo "SNAPSHOT_CREATED" >>"$MOCK_LOG"
  ;;

"rds wait")

  if [[ "$MOCK_SKIP_SNAPSHOT" == "true" ]]; then
    echo "UNEXPECTED_RDS_OPERATION" >>"$MOCK_LOG"
    exit 1
  fi

  case "${3:-}" in
  db-instance-available)
    if [[ "$MOCK_RDS_STATUS" != "stopped" ]]; then
      echo "Unexpected RDS instance waiter" >&2
      exit 1
    fi
    echo "RDS_AVAILABLE" >>"$MOCK_LOG"
    ;;
  db-snapshot-available)
    if [[ "$MOCK_SNAPSHOT_RESULT" == "wait-failure" ]]; then
      echo "SNAPSHOT_WAIT_FAILED" >>"$MOCK_LOG"
      echo "Simulated snapshot waiter failure" >&2
      exit 255
    fi

    echo "SNAPSHOT_AVAILABLE" >>"$MOCK_LOG"
    ;;
  *)
    echo "Unexpected RDS waiter: $*" >&2
    exit 1
    ;;
  esac
  ;;

*)
  echo "Unexpected AWS command: $*" >&2
  exit 1
  ;;
esac
