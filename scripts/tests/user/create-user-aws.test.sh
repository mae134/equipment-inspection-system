#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
test_dir="$(mktemp -d)"
trap 'rm -rf "$test_dir"' EXIT
mkdir "$test_dir/bin"

cat > "$test_dir/bin/aws" <<'MOCK'
#!/usr/bin/env python3
import json
import os
from pathlib import Path
import re
import signal
import stat
import sys

root = Path(os.environ["MOCK_STATE"])
scenario = os.environ["MOCK_SCENARIO"]
args = sys.argv[1:]
account, region = "339741260090", "ap-northeast-1"
ecs = f"arn:aws:ecs:{region}:{account}"
cluster = "equipment-inspection-cluster"
service = "equipment-inspection-service"
container = "equipment-inspection-app"
registry = f"{account}.dkr.ecr.{region}.amazonaws.com"
ssm = f"arn:aws:ssm:{region}:{account}:parameter"
role = f"arn:aws:iam::{account}:role/equipment-inspection-ecs-task-execution-role"
execution = os.environ["MOCK_EXECUTION"]
parameter = f"/equipment-inspection/prod/user-registration/{execution}/payload"
family = f"equipment-inspection-user-registration-{execution}"
definition = f"{ecs}:task-definition/{family}:1"
original = f"{ecs}:task-definition/equipment-inspection-task:8"
task_arn = f"{ecs}:task/{cluster}/" + "a" * 32
cluster_arn = f"{ecs}:cluster/{cluster}"
digest = "sha256:" + "d" * 64
network = {"subnets": ["subnet-12345678", "subnet-12345679"],
           "securityGroups": ["sg-12345678"], "assignPublicIp": "ENABLED"}
db = [{"name": f"SPRING_DATASOURCE_{key.upper()}", "valueFrom": f"/equipment-inspection/prod/db/{key}"}
      for key in ("url", "username", "password")]
logging = {"logDriver": "awslogs", "options": {"awslogs-group": "/ecs/equipment-inspection-task",
            "awslogs-region": region, "awslogs-stream-prefix": "ecs"}}
runtime = {"operatingSystemFamily": "LINUX", "cpuArchitecture": "X86_64"}
synthetic = '  mock-"escape\\-é-1234  '

def need(condition):
    if not condition:
        (root / "mock-assertion").touch()
        sys.exit(99)

def send(**data):
    print(json.dumps(data))

def reject(api, error):
    print(f"An error occurred ({error}) when calling the {api} operation: mock", file=sys.stderr)
    sys.exit(254)

def option(key):
    need(args.count(key) == 1)
    return args[args.index(key) + 1]

def mark(key, value=""):
    (root / key).write_text(value)

def seen(key):
    return (root / key).exists()

def task(stopped=True):
    status = "STOPPED" if stopped else "RUNNING"
    item = {"name": container, "lastStatus": status}
    if stopped and scenario != "missing-exit":
        item["exitCode"] = {"db-failure": 1, "java-invalid": 2}.get(scenario, 0)
    return dict(taskArn=task_arn, clusterArn=cluster_arn, taskDefinitionArn=definition,
                startedBy=execution, launchType="FARGATE", enableExecuteCommand=False,
                lastStatus=status, containers=[item], stopCode="EssentialContainerExited")

need(option("--profile") == "terraform-execution" and option("--region") == region)
need("--no-cli-pager" in args and "--no-cli-auto-prompt" in args and "--debug" not in args)
need(not any("mock-" in arg and "escape" in arg for arg in args))
need(not any(synthetic in value for value in os.environ.values()))
need(os.environ.get("AWS_MAX_ATTEMPTS") == "1")
service_index = next(i for i, value in enumerate(args) if value in ("configure", "sts", "ecr", "ecs", "ssm"))
category, operation = args[service_index:service_index + 2]
if category != "configure":
    endpoint = "api.ecr" if category == "ecr" else category
    need(option("--endpoint-url") == f"https://{endpoint}.{region}.amazonaws.com")
with (root / "calls").open("a") as log:
    print(operation, file=log)

if operation == "get" and category == "configure":
    if scenario == "cli-history" and args[-1] == "cli_history":
        print("enabled")
        sys.exit(0)
    sys.exit(1)
elif operation == "get-caller-identity":
    send(Account="000000000000" if scenario == "account" else account,
         Arn=f"arn:aws:sts::{account}:assumed-role/" +
         ("wrong-role" if scenario == "role" else "terraform-execution-role") + "/mock-session")
elif operation == "describe-services":
    need(option("--cluster") == cluster and option("--services") == service)
    send(failures=[], services=[] if scenario == "service" else [
        dict(status="ACTIVE", clusterArn=cluster_arn, serviceArn=f"{ecs}:service/{cluster}/{service}",
             taskDefinition=original, enableExecuteCommand=False,
             capacityProviderStrategy=[{"capacityProvider": "FARGATE"}],
             networkConfiguration={"awsvpcConfiguration": network if scenario != "bad-network" else {}})])
elif operation == "describe-task-definition":
    if option("--task-definition") == original:
        send(taskDefinition=dict(taskDefinitionArn=original, family="equipment-inspection-task",
             status="ACTIVE", executionRoleArn=role, networkMode="awsvpc",
             requiresCompatibilities=["FARGATE"], cpu="512", memory="1024", runtimePlatform=runtime,
             containerDefinitions=[dict(name=container, essential=True, image=f"{registry}/{container}:latest",
             secrets=db if scenario != "bad-secrets" else db[:2], logConfiguration=logging,
             portMappings=[{"containerPort": 8080}], healthCheck={"command": ["CMD", "false"]},
             environment=[{"name": "UNEXPECTED", "value": "ignore"}])]))
    else:
        need(option("--task-definition") == family and option("--include") == "TAGS")
        if scenario == "definition-lost":
            send(taskDefinition=dict(taskDefinitionArn=definition, family=family),
                 tags=[{"key": "UserRegistrationExecution", "value": (root / "owner").read_text()}])
        else:
            sys.exit(1)
elif operation == "describe-images":
    need(option("--registry-id") == account and option("--repository-name") == container
         and option("--image-ids") == f"imageDigest={digest}")
    send(imageDetails=[] if scenario == "absent-image" else [dict(registryId=account, repositoryName=container, imageDigest=digest)])
elif operation == "put-parameter":
    source = option("--cli-input-json")
    need(source.startswith("file:///dev/shm/aws-cli-json."))
    path = Path(source.removeprefix("file://"))
    need(stat.S_IMODE(path.stat().st_mode) == 0o600)
    mark("file", str(path))
    data = json.loads(path.read_text())
    need(data["Name"] == parameter and data["Type"] == "SecureString" and data["Tier"] == "Standard"
         and data["Overwrite"] is False and "KeyId" not in data)
    payload = json.loads(data["Value"])
    need(payload == {"name": 'Mock "quoted" \\ user', "email": "mock@example.invalid",
                     "role": os.environ["MOCK_ROLE"], "password": synthetic}
         and len(data["Value"].encode()) <= 4096)
    need(len(data["Tags"]) == 1 and data["Tags"][0]["Key"] == "UserRegistrationExecution")
    owner = data["Tags"][0]["Value"]
    need(re.fullmatch(r"[a-f0-9-]{36}", owner) and owner != execution)
    mark("owner", owner)
    if scenario in ("ssm-rejection", "ssm-exists"):
        reject("PutParameter", "ParameterAlreadyExists" if scenario == "ssm-exists" else "AccessDeniedException")
    if scenario in ("ssm-lost", "ssm-unresolved"):
        print(synthetic, file=sys.stderr)
        sys.exit(1)
    send(Version=1, Tier="Standard")
elif operation == "list-tags-for-resource":
    need(option("--resource-type") == "Parameter" and option("--resource-id") == parameter)
    send(TagList=[{"Key": "UserRegistrationExecution", "Value":
                   (root / "owner").read_text() if scenario == "ssm-lost" else execution}])
elif operation == "register-task-definition":
    data = json.loads(option("--cli-input-json"))
    need(data == dict(family=family, executionRoleArn=role, networkMode="awsvpc",
                      requiresCompatibilities=["FARGATE"], cpu="512", memory="1024", runtimePlatform=runtime,
                      tags=[{"key": "UserRegistrationExecution", "value": (root / "owner").read_text()}],
                      containerDefinitions=[dict(name=container, essential=True, image=f"{registry}/{container}@{digest}",
                        command=["--user-registration=aws"], logConfiguration=logging,
                        secrets=db + [dict(name="USER_REGISTRATION_PAYLOAD", valueFrom=ssm + parameter)])]))
    if scenario == "definition-rejection":
        reject("RegisterTaskDefinition", "AccessDeniedException")
    if scenario in ("definition-lost", "definition-unresolved"):
        sys.exit(1)
    send(taskDefinition=dict(taskDefinitionArn=definition, family=family))
elif operation == "run-task":
    need(not seen("ran") and option("--task-definition") == definition and option("--cluster") == cluster)
    need(option("--count") == "1" and option("--launch-type") == "FARGATE"
         and option("--client-token") == execution and option("--started-by") == execution
         and "--disable-execute-command" in args and "--enable-execute-command" not in args
         and json.loads(option("--network-configuration")) == {"awsvpcConfiguration": network})
    mark("ran")
    if scenario == "run-failure":
        send(tasks=[], failures=[{"reason": "mock"}])
    elif scenario in ("run-lost", "run-empty", "run-ambiguous"):
        sys.exit(1)
    elif scenario == "run-inconsistent":
        send(tasks=[task()], failures=[{"reason": "mock"}])
    else:
        send(tasks=[task(False)], failures=[])
elif operation == "list-tasks":
    need(option("--cluster") == cluster)
    if "--started-by" in args:
        need(option("--started-by") == execution and "--family" not in args and "--desired-status" not in args)
        send(taskArns=[])
    else:
        need(option("--family") == family and option("--desired-status") == "STOPPED")
        if scenario == "run-lost" and not seen("not-visible"):
            mark("not-visible")
            send(taskArns=[])
            sys.exit(0)
        send(taskArns={"run-lost": [task_arn], "run-ambiguous": [task_arn, task_arn[:-1] + "b"]}.get(scenario, []))
elif operation == "describe-tasks":
    need(option("--cluster") == cluster and option("--tasks") == task_arn)
    if scenario == "transient" and not seen("transient"):
        mark("transient")
        sys.exit(1)
    if scenario in ("int", "term", "stop-fail", "stop-unconfirmed") and not seen("signal"):
        mark("signal")
        os.kill(int(os.environ["MOCK_LAUNCHER_PID"]), signal.SIGINT if scenario == "int" else signal.SIGTERM)
    pending = scenario in ("timeout", "int", "term", "stop-fail", "stop-unconfirmed")
    stopped = not pending or (seen("stop") and scenario not in ("stop-fail", "stop-unconfirmed"))
    if stopped:
        mark("stopped")
    send(tasks=[task(stopped)], failures=[])
elif operation == "stop-task":
    need(option("--cluster") == cluster and option("--task") == task_arn)
    mark("stop")
    if scenario == "stop-fail":
        sys.exit(1)
    send(task=task(False))
elif operation == "delete-parameter":
    need(option("--name") == parameter and (not seen("ran") or seen("stopped") or scenario == "run-failure"))
    if scenario == "delete-failure":
        sys.exit(1)
elif operation == "deregister-task-definition":
    need(option("--task-definition") == definition and
         (not seen("ran") or seen("stopped") or scenario == "run-failure"))
    if scenario == "deregister-failure":
        sys.exit(1)
else:
    need(False)
MOCK

cat > "$test_dir/bin/date" <<'MOCK'
#!/bin/bash
[[ $* == +%s ]] || exit 99
read -r time < "$MOCK_STATE/clock"
printf '%s\n' "$time"
MOCK
cat > "$test_dir/bin/sleep" <<'MOCK'
#!/bin/bash
read -r time < "$MOCK_STATE/clock"
printf '%s\n' "$((time + $1))" > "$MOCK_STATE/clock"
MOCK
cat > "$test_dir/bin/jq" <<'MOCK'
#!/bin/bash
if [[ $MOCK_SCENARIO == payload-too-large && $* == *'select(.[3] | length >= 12'* ]]; then
  /bin/cat >/dev/null
  printf '%04097d\n' 0
else
  exec "$MOCK_REAL_JQ" "$@"
fi
MOCK
chmod +x "$test_dir/bin/"*
export MOCK_REAL_JQ
MOCK_REAL_JQ="$(type -P jq)"
mkdir "$test_dir/missing"
ln -s "$(type -P bash)" "$test_dir/missing/bash"

python3 - "$script_dir/../../user/create-user-aws.sh" "$test_dir" <<'PY'
import errno
import os
from pathlib import Path
import pty
import select
import signal
import subprocess
import sys
import termios
import time
import uuid

launcher = Path(sys.argv[1]).resolve()
root = Path(sys.argv[2])
synthetic = '  mock-"escape\\-é-1234  '
digest = "sha256:" + "d" * 64

def exercise(case, status, *, role="ADMIN", inputs=True, password=synthetic, confirmation=None,
             confirm="y", email=" mock@example.invalid ", noninteractive=False, overrides=None, expected=(), message=None):
    state = root / case
    state.mkdir()
    (state / "clock").write_text("1000\n")
    execution, owner = str(uuid.uuid4()), str(uuid.uuid4())
    uuidgen = state / "uuidgen"
    uuidgen.write_text(f'#!/bin/bash\n'
                       f'if [[ -f "$MOCK_STATE/uuid-first" ]]; then printf "%s\\n" {owner}; '
                       f'else : > "$MOCK_STATE/uuid-first"; printf "%s\\n" {execution}; fi\n')
    uuidgen.chmod(0o700)
    tools = state / "tools"
    tools.mkdir()
    (tools / "uuidgen").symlink_to(uuidgen)
    env = dict(PATH=f"{tools}:{root / 'bin'}:/usr/bin:/bin", LANG="C.UTF-8",
               USER_REGISTRATION_IMAGE_DIGEST=digest, MOCK_STATE=str(state), MOCK_SCENARIO=case,
               MOCK_ROLE=role, MOCK_EXECUTION=execution, MOCK_REAL_JQ=os.environ["MOCK_REAL_JQ"])
    env.update(overrides or {})
    output = bytearray()
    if noninteractive:
        result = subprocess.run([launcher], env=env, capture_output=True, input=b"", timeout=10)
        code = result.returncode
        output.extend(result.stdout + result.stderr)
    else:
        pid, fd = pty.fork()
        if pid == 0:
            env["MOCK_LAUNCHER_PID"] = str(os.getpid())
            os.execve(launcher, [launcher], env)
        waited = False
        cursor = 0
        deadline = time.monotonic() + 35

        def receive():
            remaining = deadline - time.monotonic()
            assert remaining > 0 and select.select([fd], [], [], remaining)[0], (case, "timed out")
            try:
                return os.read(fd, 65536)
            except OSError as exc:
                if exc.errno == errno.EIO:
                    return b""
                raise

        def answer(prompt, value, hidden=False):
            nonlocal cursor
            while (index := output.find(prompt, cursor)) < 0:
                chunk = receive()
                assert chunk, (case, "prompt missing", prompt)
                output.extend(chunk)
            cursor = index + len(prompt)
            if hidden:
                assert not termios.tcgetattr(fd)[3] & termios.ECHO, case
            os.write(fd, value.encode() + b"\n")

        try:
            if inputs:
                answer(b"Name: ", 'Mock "quoted" \\ user')
                answer(b"Email: ", email)
                answer(b"Role (ADMIN or INSPECTOR): ", role)
                if role in ("ADMIN", "INSPECTOR") and case != "email-invalid":
                    answer(b"Password: ", password, True)
                    answer(b"Confirm password: ", confirmation if confirmation is not None else password, True)
                    if case != "payload-too-large" and confirmation is None and len(password.encode()) <= 72 and len(password) >= 12:
                        answer(b"Confirm CLI image and registration [y/N]: ", confirm)
            while (chunk := receive()):
                output.extend(chunk)
            _, result = os.waitpid(pid, 0)
            waited = True
            code = os.waitstatus_to_exitcode(result)
        finally:
            os.close(fd)
            if not waited:
                try:
                    os.kill(pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                os.waitpid(pid, 0)

    assert synthetic.encode() not in output, (case, "synthetic password exposed")
    assert code == status, (case, "unexpected exit status", code)
    assert not (state / "mock-assertion").exists(), (case, "unexpected AWS call or arguments")
    calls = (state / "calls").read_text().splitlines() if (state / "calls").exists() else []
    writes = [op for op in calls if op in ("put-parameter", "register-task-definition", "run-task",
                                            "stop-task", "delete-parameter", "deregister-task-definition")]
    assert writes == list(expected), (case, writes)
    if "put-parameter" in writes:
        assert not Path((state / "file").read_text()).exists(), (case, "temporary payload file remains")
    if status == 4:
        assert execution.encode() in output and b"Task definition:" in output and b"Parameter:" in output, case
    if message:
        assert message in output, (case, "expected status message missing")
    print("PASS:", case)

put = ("put-parameter",)
definition = put + ("register-task-definition",)
run = definition + ("run-task",)
delete = ("delete-parameter",)
clean = delete + ("deregister-task-definition",)
stop = ("stop-task",)
exercise("missing-prerequisite", 1, inputs=False, noninteractive=True, overrides={"PATH": str(root / "missing")},
         message=b"Required command missing: aws")
exercise("no-terminal", 1, inputs=False, noninteractive=True)
exercise("digest-missing", 2, inputs=False, overrides={"USER_REGISTRATION_IMAGE_DIGEST": ""})
exercise("digest-invalid", 2, inputs=False, overrides={"USER_REGISTRATION_IMAGE_DIGEST": "latest"})
exercise("endpoint-override", 1, inputs=False, overrides={"AWS_ENDPOINT_URL_ECS": "https://invalid.example"})
for case in ("account", "role", "service", "bad-network", "bad-secrets", "absent-image", "cli-history"):
    exercise(case, 1, inputs=False)
exercise("role-invalid", 2, role="MANAGER")
exercise("email-invalid", 2, email="mock@example..invalid")
exercise("password-mismatch", 2, confirmation="different synthetic input")
exercise("invalid-utf8-byte-length", 2, password="é" * 37)
exercise("password-too-short", 2, password="short")
exercise("payload-too-large", 2, message=b"4096-byte SSM limit")
exercise("cancellation", 3, confirm="n")
exercise("admin-success", 0, expected=run + clean, message=b"Registration succeeded.")
exercise("inspector-success", 0, role="INSPECTOR", expected=run + clean)
exercise("ssm-rejection", 1, expected=put)
exercise("ssm-exists", 1, expected=put)
exercise("ssm-lost", 1, expected=put + delete)
exercise("ssm-unresolved", 4, expected=put)
exercise("definition-rejection", 1, expected=definition + delete)
exercise("definition-lost", 1, expected=definition + clean)
exercise("definition-unresolved", 4, expected=definition + delete)
exercise("run-failure", 1, expected=run + clean)
exercise("run-lost", 0, expected=run + clean)
for case in ("run-empty", "run-ambiguous", "run-inconsistent"):
    exercise(case, 4, expected=run, message=b"Verify database registration status before retrying.")
exercise("db-failure", 1, expected=run + clean)
exercise("java-invalid", 2, expected=run + clean)
exercise("missing-exit", 4, expected=run + clean)
exercise("transient", 0, expected=run + clean)
for case in ("timeout", "int", "term"):
    exercise(case, 4, expected=run + stop + clean)
for case in ("stop-fail", "stop-unconfirmed"):
    exercise(case, 4, expected=run + stop)
for case in ("delete-failure", "deregister-failure"):
    exercise(case, 4, expected=run + clean,
             message=b"Registration succeeded.\r\nCleanup incomplete or could not be confirmed.")
PY
