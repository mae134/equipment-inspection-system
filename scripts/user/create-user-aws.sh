#!/usr/bin/env bash

# jq variables are expanded by jq. Cleanup functions are invoked by traps.
# shellcheck disable=SC2016,SC2317,SC1091

set +ax
set -euo pipefail

fail() {
  printf 'Error: %s\n' "$2" >&2
  exit "$1"
}

(($# == 0)) || fail 2 'Use create-user.sh --aws without additional arguments.'
for tool in aws jq timeout uuidgen iconv stat mktemp cat rm sleep dirname date; do
  command -v "$tool" >/dev/null || fail 1 "Required command missing: $tool."
done
[[ -t 0 && -t 1 && -t 2 ]] || fail 1 'AWS registration requires an interactive terminal.'
[[ $(stat -f -c %T /dev/shm) == tmpfs && -w /dev/shm ]] ||
  fail 1 'A writable /dev/shm tmpfs is required.'

digest=${USER_REGISTRATION_IMAGE_DIGEST:-}
[[ $digest =~ ^sha256:[0-9a-f]{64}$ ]] ||
  fail 2 'Set USER_REGISTRATION_IMAGE_DIGEST to sha256 followed by 64 lowercase hexadecimal digits.'

for setting in "${!AWS_ENDPOINT_URL@}" AWS_CA_BUNDLE AWS_DATA_PATH AWS_CONFIG_FILE \
  AWS_SHARED_CREDENTIALS_FILE AWS_CLI_HISTORY_FILE AWS_CONTAINER_CREDENTIALS_FULL_URI AWS_CONTAINER_CREDENTIALS_RELATIVE_URI \
  AWS_WEB_IDENTITY_TOKEN_FILE AWS_ACCOUNT_ID AWS_STS_REGIONAL_ENDPOINTS \
  HTTP_PROXY HTTPS_PROXY ALL_PROXY http_proxy https_proxy all_proxy REQUESTS_CA_BUNDLE SSL_CERT_FILE SSL_CERT_DIR; do
  [[ ! -v $setting ]] || fail 1 'Remove custom AWS endpoint, proxy, credential source, and certificate overrides.'
done
export AWS_IGNORE_CONFIGURED_ENDPOINT_URLS=true AWS_PAGER='' AWS_CLI_AUTO_PROMPT=off
export AWS_MAX_ATTEMPTS=1 AWS_EC2_METADATA_DISABLED=true AWS_USE_FIPS_ENDPOINT=false AWS_USE_DUALSTACK_ENDPOINT=false
export AWS_ACCOUNT_ID_ENDPOINT_MODE=disabled AWS_CLI_FILE_ENCODING=UTF-8
umask 077
ulimit -c 0

script_dir="$(CDPATH='' cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
# shellcheck source=scripts/lib/aws-cli-json.sh
source "$script_dir/../lib/aws-cli-json.sh"

readonly account=339741260090 region=ap-northeast-1 profile=terraform-execution
readonly cluster=equipment-inspection-cluster service=equipment-inspection-service
readonly container=equipment-inspection-app tag_key=UserRegistrationExecution
readonly ecs="arn:aws:ecs:$region:$account" ssm="arn:aws:ssm:$region:$account:parameter"
readonly execution_role="arn:aws:iam::$account:role/equipment-inspection-ecs-task-execution-role"
readonly cluster_arn="$ecs:cluster/$cluster" service_arn="$ecs:service/$cluster/$service"
readonly registry="$account.dkr.ecr.$region.amazonaws.com"
deadline=0

clock_seconds() { date +%s; }

aws_bounded() {
  local limit=20 remaining endpoint=()
  if ((deadline)); then
    remaining=$((deadline - $(clock_seconds)))
    ((remaining > 1)) || return 124
    if ((remaining <= limit)); then limit=$((remaining - 1)); fi
  fi
  case $1 in
    sts|ecs|ssm) endpoint=(--endpoint-url "https://$1.$region.amazonaws.com") ;;
    ecr) endpoint=(--endpoint-url "https://api.ecr.$region.amazonaws.com") ;;
  esac
  timeout --kill-after=1s "${limit}s" aws --profile "$profile" --region "$region" \
    --no-cli-pager --no-cli-auto-prompt --output json --cli-connect-timeout 5 --cli-read-timeout 10 "${endpoint[@]}" "$@" </dev/null
}

aws_read() {
  aws_bounded "$@" 2>/dev/null
}

# All parsing is quiet, even if an API returns malformed data.
json() {
  jq -cer "$@" 2>/dev/null
}

# CLI history records API request bodies. Refuse it before handling credentials.
for key in cli_history ca_bundle; do
  if value=$(aws_bounded configure get "$key" 2>&1); then
    [[ $key == cli_history && $value == disabled ]] ||
      fail 1 'Disable AWS CLI history and remove custom CA bundles from the AWS profile.'
  elif [[ $? != 1 || -n $value ]]; then
    fail 1 'Could not verify AWS CLI logging and certificate settings.'
  fi
done
unset value

identity=$(aws_read sts get-caller-identity) || fail 1 'Could not verify AWS caller identity.'
json --arg account "$account" --arg prefix "arn:aws:sts::$account:assumed-role/terraform-execution-role/" '
  .Account == $account and (.Arn | startswith($prefix) and (ltrimstr($prefix) | test("^[A-Za-z0-9+=,.@_-]{2,64}$")))
' <<< "$identity" >/dev/null || fail 1 'Unexpected AWS account or assumed role.'

services=$(aws_read ecs describe-services --cluster "$cluster" --services "$service") ||
  fail 1 'Could not discover the ECS service.'
service_info=$(json --arg cluster "$cluster_arn" --arg service "$service_arn" '
  def ids($prefix): type == "array" and length > 0 and (unique | length) == length
    and all(.[]; type == "string" and test("^" + $prefix + "-([a-f0-9]{8}|[a-f0-9]{17})$"));
  select(.failures == [] and (.services | length) == 1) | .services[0]
  | select(.status == "ACTIVE" and .clusterArn == $cluster and .serviceArn == $service
      and .enableExecuteCommand == false and
      ((.launchType == "FARGATE" and (.capacityProviderStrategy // []) == []) or
       ((.capacityProviderStrategy | length) == 1 and .capacityProviderStrategy[0].capacityProvider == "FARGATE")))
  | select(.networkConfiguration.awsvpcConfiguration |
      (.subnets | ids("subnet")) and (.securityGroups | ids("sg")) and
      (.assignPublicIp == "ENABLED" or .assignPublicIp == "DISABLED"))
  | {taskDefinition, network: {awsvpcConfiguration: (.networkConfiguration.awsvpcConfiguration |
       {subnets, securityGroups, assignPublicIp})}}
' <<< "$services") || fail 1 'Unexpected ECS service or network configuration.'
source_arn=$(json '.taskDefinition' <<< "$service_info")
source_prefix="$ecs:task-definition/equipment-inspection-task:"
[[ $source_arn == "$source_prefix"* && ${source_arn#"$source_prefix"} =~ ^[1-9][0-9]*$ ]] ||
  fail 1 'Unexpected application task definition ARN.'
network=$(json '.network' <<< "$service_info")

definition=$(aws_read ecs describe-task-definition --task-definition "$source_arn") ||
  fail 1 'Could not discover the application task definition.'
template=$(json --arg arn "$source_arn" --arg role "$execution_role" --arg name "$container" \
  --arg repo "$registry/$container" --arg region "$region" --arg ssm "$ssm" '
  .taskDefinition
  | select(.taskDefinitionArn == $arn and .status == "ACTIVE" and .family == "equipment-inspection-task"
      and .executionRoleArn == $role and .taskRoleArn == null and .networkMode == "awsvpc"
      and .requiresCompatibilities == ["FARGATE"] and .runtimePlatform.operatingSystemFamily == "LINUX"
      and (.runtimePlatform.cpuArchitecture == "X86_64" or .runtimePlatform.cpuArchitecture == "ARM64")
      and (.cpu | test("^[1-9][0-9]*$")) and (.memory | test("^[1-9][0-9]*$"))
      and (.containerDefinitions | length) == 1)
  | .containerDefinitions[0] as $c
  | {SPRING_DATASOURCE_URL: "/equipment-inspection/prod/db/url",
     SPRING_DATASOURCE_USERNAME: "/equipment-inspection/prod/db/username",
     SPRING_DATASOURCE_PASSWORD: "/equipment-inspection/prod/db/password"} as $db
  | select($c.name == $name and $c.essential == true and
      ($c.image | startswith($repo + ":") or startswith($repo + "@sha256:")) and
      (($c.entryPoint // []) == [] or $c.entryPoint == ["java","-jar","app.jar"]) and
      ($c.secrets | length) == 3 and
      ([$c.secrets[].name] | sort) == ($db | keys) and
      all($c.secrets[]; . as $s | .valueFrom == $db[$s.name] or .valueFrom == ($ssm + $db[$s.name])) and
      ($c.logConfiguration | .logDriver == "awslogs" and (.secretOptions // []) == [] and
        .options["awslogs-group"] == "/ecs/equipment-inspection-task" and
        .options["awslogs-region"] == $region and .options["awslogs-stream-prefix"] == "ecs"))
  | {networkMode, requiresCompatibilities, cpu, memory, runtimePlatform: (.runtimePlatform |
      {operatingSystemFamily,cpuArchitecture}), executionRoleArn,
      containerDefinitions: [($c | {name,essential,secrets: [.secrets[] | {name,valueFrom}],
        logConfiguration: {logDriver: .logConfiguration.logDriver, options: (.logConfiguration.options |
          {"awslogs-group","awslogs-region","awslogs-stream-prefix"})}})]}
' <<< "$definition") || fail 1 'Unexpected execution role, task settings, or database secret references.'

images=$(aws_read ecr describe-images --registry-id "$account" --repository-name "$container" \
  --image-ids "imageDigest=$digest") || fail 1 'Could not find the selected ECR image digest.'
json --arg digest "$digest" --arg repo "$container" --arg account "$account" '
  (.imageDetails | length) == 1 and (.imageDetails[0] |
    .imageDigest == $digest and .repositoryName == $repo and .registryId == $account)
' <<< "$images" >/dev/null || fail 1 'Unexpected ECR image metadata.'

uuid_pattern='^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$'
execution=$(uuidgen --random) || fail 1 'Could not generate an execution identifier.'
ownership=$(uuidgen --random) || fail 1 'Could not generate a resource ownership identifier.'
[[ $execution =~ $uuid_pattern && $ownership =~ $uuid_pattern && $execution != "$ownership" ]] ||
  fail 1 'Invalid execution identifiers.'
parameter="/equipment-inspection/prod/user-registration/$execution/payload"
family="equipment-inspection-user-registration-$execution"
definition_arn="$ecs:task-definition/$family:1"
task_prefix="$ecs:task/$cluster/"

cancel() { printf 'Registration cancelled.\n'; exit 3; }

# Keep private values in unexported locals and only serialize them into a pipe.
collect_payload() {
  local name email role password confirmation answer payload
  export -n password confirmation payload
  IFS= read -r -p 'Name: ' name || cancel
  IFS= read -r -p 'Email: ' email || cancel
  IFS= read -r -p 'Role (ADMIN or INSPECTOR): ' role || cancel
  email="${email#"${email%%[![:space:]]*}"}"
  email="${email%"${email##*[![:space:]]}"}"
  local email_pattern='^[^@[:space:]]+@([[:alnum:]]([[:alnum:]-]*[[:alnum:]])?)(\.[[:alnum:]]([[:alnum:]-]*[[:alnum:]])?)*$'
  [[ $email =~ $email_pattern && ${email%%@*} != .* && ${email%%@*} != *. &&
     ${email%%@*} != *..* && ( $role == ADMIN || $role == INSPECTOR ) ]] ||
    fail 2 'Enter a valid email and select ADMIN or INSPECTOR.'
  printf '%s\0%s' "$name" "$email" | iconv -f UTF-8 -t UTF-8 2>/dev/null |
    json -Rs 'split("\u0000") | (.[0] | length <= 100 and test("\\S")) and
      (.[1] | length <= 255) and all(.[]; test("[\\p{C}]") | not)' >/dev/null ||
    fail 2 'Enter valid UTF-8 name and email without control characters.'
  IFS= read -rs -p 'Password: ' password || { printf '\n'; cancel; }
  printf '\n'
  IFS= read -rs -p 'Confirm password: ' confirmation || { printf '\n'; cancel; }
  printf '\n'
  [[ $password == "$confirmation" ]] || fail 2 'Passwords do not match.'
  unset confirmation
  payload=$(printf '%s\0%s\0%s\0%s' "$name" "$email" "$role" "$password" |
    iconv -f UTF-8 -t UTF-8 2>/dev/null |
    json -Rs 'split("\u0000") | select(.[3] | length >= 12 and utf8bytelength <= 72 and test("\\S"))
      | {name: .[0],email: .[1],role: .[2],password: .[3]}') ||
    fail 2 'Password must contain at least 12 characters and at most 72 UTF-8 bytes.'
  unset password
  printf '%s' "$payload" | json -Rs 'utf8bytelength <= 4096' >/dev/null ||
    fail 2 'Registration payload exceeds the 4096-byte SSM limit.'

  printf 'AWS destination: %s / %s / %s / production database\n' "$account" "$region" "$cluster" >&2
  printf 'Image digest: %s\nName: %s\nEmail: %s\nRole: %s\n' "$digest" "$name" "$email" "$role" >&2
  printf 'ECR confirms digest existence only. Verify this image contains the registration CLI.\n' >&2
  IFS= read -r -p 'Confirm CLI image and registration [y/N]: ' answer || cancel
  [[ $answer == y || $answer == Y ]] || cancel
  registration_payload="$payload"
}

ssm_state=none
definition_state=none
launch_state=none
task_arn=''
task_status=''
task_document=''
registration_status=1
registration_known=false
cleanup_failed=false

valid_task_arn() {
  [[ $1 == "$task_prefix"* && ${1#"$task_prefix"} =~ ^[0-9a-f]{32}$ ]]
}

verified_task() {
  json --arg cluster "$cluster_arn" --arg definition "$definition_arn" \
    --arg execution "$execution" --arg arn "$1" '
    select(.failures == [] and (.tasks | length) == 1) | .tasks[0]
    | select(.taskArn == $arn and .clusterArn == $cluster and .taskDefinitionArn == $definition
        and .startedBy == $execution and .launchType == "FARGATE" and .enableExecuteCommand == false
        and (.lastStatus | IN("PROVISIONING","PENDING","ACTIVATING","RUNNING","DEACTIVATING","STOPPING","DEPROVISIONING","STOPPED")))
  '
}

observe_task() {
  local response
  response=$(aws_read ecs describe-tasks --cluster "$cluster" --tasks "$task_arn") || return 1
  task_document=$(verified_task "$task_arn" <<< "$response") || return 1
  task_status=$(json '.lastStatus' <<< "$task_document")
}

pause_polling() {
  local pause=$1 remaining
  if ((deadline)); then
    remaining=$((deadline - $(clock_seconds)))
    if ((remaining < pause)); then pause=$remaining; fi
  fi
  if ((pause > 0)); then sleep "$pause"; fi
}

wait_stopped() {
  local pause=2 poll
  for ((poll=0; poll<150 && $(clock_seconds) < deadline - 1; poll++)); do
    if observe_task && [[ $task_status == STOPPED ]]; then
      launch_state=stopped
      return 0
    fi
    pause_polling "$pause"
    if ((pause < 10)); then pause=$((pause * 2)); fi
  done
  return 1
}

reconcile_task() {
  local running stopped candidates candidate delay
  # ECS only supports startedBy as a sole filter. Search stopped tasks by family,
  # then verify both lists against the exact definition and the UUID sent as clientToken/startedBy.
  for delay in 1 2 4 8 16; do
    if running=$(aws_read ecs list-tasks --cluster "$cluster" --started-by "$execution") &&
       stopped=$(aws_read ecs list-tasks --cluster "$cluster" --family "$family" --desired-status STOPPED) &&
       candidates=$(printf '%s\n%s\n' "$running" "$stopped" | json -cs '
         select(length == 2 and all(.[]; (.taskArns | type) == "array" and .nextToken == null))
         | [.[].taskArns[]] | unique'); then
      case $(json 'length' <<< "$candidates") in
        0) ;;
        1)
          candidate=$(json '.[0]' <<< "$candidates")
          if valid_task_arn "$candidate"; then
            task_arn=$candidate
            if observe_task; then
              launch_state=confirmed
              return 0
            fi
            task_arn=''
          fi
          ;;
        *) return 1 ;;
      esac
    fi
    pause_polling "$delay"
  done
  return 1
}

reconcile_ssm() {
  local response delay
  for delay in 1 2 4; do
    if response=$(aws_read ssm list-tags-for-resource --resource-type Parameter --resource-id "$parameter") &&
       json --arg key "$tag_key" --arg value "$ownership" '
         [.TagList[] | select(.Key == $key)] == [{Key: $key, Value: $value}]
       ' <<< "$response" >/dev/null; then
      ssm_state=confirmed
      return 0
    fi
    pause_polling "$delay"
  done
  return 1
}

reconcile_definition() {
  local response delay
  for delay in 1 2 4; do
    if response=$(aws_read ecs describe-task-definition --task-definition "$family" --include TAGS) &&
       json --arg arn "$definition_arn" --arg family "$family" --arg key "$tag_key" --arg value "$ownership" '
         .taskDefinition.taskDefinitionArn == $arn and .taskDefinition.family == $family and
         [.tags[] | select(.key == $key)] == [{key: $key, value: $value}]
       ' <<< "$response" >/dev/null; then
      definition_state=confirmed
      return 0
    fi
    pause_polling "$delay"
  done
  return 1
}

confirmed_rejection() {
  local code_pattern='^An error occurred \((AccessDenied|AccessDeniedException|ValidationException|InvalidParameterException|InvalidParameters|ParameterLimitExceeded|ParameterAlreadyExists)\) when calling the '
  [[ $1 =~ $code_pattern && $1 != *$'\n'* ]]
}

recovery_ids() {
  printf 'Execution UUID: %s\nParameter: %s\nTask definition: %s\n' "$execution" "$parameter" "$definition_arn" >&2
}

finish() {
  local original_status=$1
  trap - EXIT
  trap '' INT TERM
  unset registration_payload payload password confirmation
  set +e

  if [[ $launch_state == attempted || $launch_state == ambiguous || $launch_state == confirmed ]]; then
    deadline=$(($(clock_seconds) + 300))
    if [[ $launch_state == attempted ]]; then reconcile_task; fi
    if [[ $launch_state == confirmed ]]; then
      if observe_task && [[ $task_status == STOPPED ]]; then
        launch_state=stopped
      else
        if ! aws_read ecs stop-task --cluster "$cluster" --task "$task_arn" \
          --reason 'User registration interrupted or timed out' >/dev/null; then
          printf 'StopTask could not be confirmed. Checking task termination.\n' >&2
        fi
        wait_stopped
      fi
    fi
  fi
  deadline=0

  case $launch_state in
    attempted|confirmed|ambiguous)
      cleanup_failed=true
      ;;
    *)
      if [[ $ssm_state == attempted ]]; then reconcile_ssm || cleanup_failed=true; fi
      if [[ $definition_state == attempted ]]; then reconcile_definition || cleanup_failed=true; fi
      if [[ $ssm_state == confirmed ]]; then
        aws_read ssm delete-parameter --name "$parameter" >/dev/null ||
          { printf 'Could not confirm temporary parameter deletion.\n' >&2; cleanup_failed=true; }
      fi
      if [[ $definition_state == confirmed ]]; then
        aws_read ecs deregister-task-definition --task-definition "$definition_arn" >/dev/null ||
          { printf 'Could not confirm temporary task definition deregistration.\n' >&2; cleanup_failed=true; }
      fi
      ;;
  esac

  if [[ $registration_known == true ]]; then
    if ((registration_status == 0)); then
      printf 'Registration succeeded.\n'
    else
      printf 'Registration failed (container exit %s).\n' "$registration_status" >&2
    fi
  elif [[ $launch_state != none && $launch_state != rejected ]]; then
    printf 'Registration outcome unknown. Verify database registration status before retrying.\n' >&2
    original_status=4
  fi
  if [[ $cleanup_failed == true || $original_status == 4 ]]; then
    if [[ $cleanup_failed == true ]]; then
      printf 'Cleanup incomplete or could not be confirmed.\n' >&2
    fi
    recovery_ids
    if [[ -n $task_arn ]]; then printf 'Task ARN: %s\n' "$task_arn" >&2; fi
    exit 4
  fi
  exit "$original_status"
}

interrupted() {
  printf 'Registration interrupted.\n' >&2
  if [[ $launch_state == none || $launch_state == rejected ]]; then exit 3; fi
  exit 4
}

trap 'finish "$?"' EXIT
trap interrupted INT TERM
unset registration_payload payload result
collect_payload
recovery_ids

ssm_state=attempted
# Collect errors privately. Only recognized API error codes influence cleanup.
if result=$( {
  printf '%s' "$registration_payload" |
    json -Rs --arg name "$parameter" --arg key "$tag_key" --arg value "$ownership" '
      {Name: $name, Type: "SecureString", Tier: "Standard", Overwrite: false,
       Value: ., Tags: [{Key: $key, Value: $value}]}' |
    aws_cli_with_secure_json aws_bounded ssm put-parameter
} 2>&1 ); then
  unset registration_payload
  if printf '%s' "$result" | json '.Version == 1 and .Tier == "Standard"' >/dev/null; then
    ssm_state=confirmed
  fi
elif confirmed_rejection "$result"; then
  ssm_state=rejected
fi
unset result registration_payload
[[ $ssm_state == confirmed ]] || fail 1 'Could not confirm creation of the temporary SSM parameter.'

request=$(json --arg family "$family" --arg image "$registry/$container@$digest" \
  --arg parameter "$ssm$parameter" --arg key "$tag_key" --arg value "$ownership" '
  .family = $family | .tags = [{key: $key, value: $value}] |
  .containerDefinitions[0] |= (.image = $image | .command = ["--user-registration=aws"] |
    .secrets += [{name: "USER_REGISTRATION_PAYLOAD", valueFrom: $parameter}])
' <<< "$template")
definition_state=attempted
if result=$(aws_bounded ecs register-task-definition --cli-input-json "$request" 2>&1); then
  if json --arg arn "$definition_arn" --arg family "$family" '
    .taskDefinition.taskDefinitionArn == $arn and .taskDefinition.family == $family
  ' <<< "$result" >/dev/null; then
    definition_state=confirmed
  fi
elif confirmed_rejection "$result"; then
  definition_state=rejected
fi
unset result
[[ $definition_state == confirmed ]] || fail 1 'Could not confirm registration of the temporary task definition.'

deadline=$(($(clock_seconds) + 900))
launch_state=attempted
if result=$(aws_bounded ecs run-task --cluster "$cluster" --task-definition "$definition_arn" \
  --launch-type FARGATE --count 1 --client-token "$execution" --started-by "$execution" \
  --disable-execute-command --network-configuration "$network" 2>&1); then
  if json '(.tasks == []) and (.failures | type == "array" and length > 0)' <<< "$result" >/dev/null; then
    launch_state=rejected
  elif candidate=$(json 'select(.failures == [] and (.tasks | length) == 1) | .tasks[0].taskArn' <<< "$result") &&
    valid_task_arn "$candidate" && verified_task "$candidate" <<< "$result" >/dev/null; then
    task_arn=$candidate
    launch_state=confirmed
  elif json '(.tasks | type == "array" and length > 1) or
    ((.tasks | length) == 1 and (.failures | length) > 0)' <<< "$result" >/dev/null; then
    launch_state=ambiguous
  fi
elif confirmed_rejection "$result"; then
  launch_state=rejected
fi
unset result
[[ $launch_state != rejected ]] || fail 1 'ECS rejected the standalone registration task.'
if [[ $launch_state == attempted ]]; then
  if ! reconcile_task; then
    launch_state=ambiguous
    fail 4 'Could not identify exactly one registration task.'
  fi
fi
[[ $launch_state == confirmed ]] || fail 4 'ECS returned an ambiguous launch result.'
wait_stopped || fail 4 'Registration task monitoring timed out.'

exit_code=$(json --arg name "$container" '
  select(.lastStatus == "STOPPED" and .stopCode == "EssentialContainerExited") |
  select((.containers | length) == 1 and .containers[0].name == $name and .containers[0].lastStatus == "STOPPED")
  | .containers[0].exitCode | select(type == "number" and . == floor and . >= 0 and . <= 255)
' <<< "$task_document") || fail 4 'ECS did not return a definitive registration container result.'
registration_known=true
registration_status=$exit_code
case $exit_code in
  0|2|3) exit "$exit_code" ;;
  *) exit 1 ;;
esac
