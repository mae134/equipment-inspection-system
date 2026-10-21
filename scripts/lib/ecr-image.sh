#!/usr/bin/env bash

build_and_push_ecr_image() {
  local repository_name="equipment-inspection-app"
  local account_id="339741260090"
  local registry="${account_id}.dkr.ecr.${AWS_REGION}.amazonaws.com"
  local image_uri="${registry}/${repository_name}:latest"

  echo "Building application JAR..."

  (
    cd "$PROJECT_ROOT" || exit 1
    "${MVNW_COMMAND:-./mvnw}" -B verify
  ) || return 1

  echo "Building Docker image..."

  docker build \
    -t "$image_uri" \
    "$PROJECT_ROOT" || return 1

  echo "Logging in to Amazon ECR..."

  aws ecr get-login-password \
    --profile "$AWS_PROFILE" \
    --region "$AWS_REGION" |
    docker login \
      --username AWS \
      --password-stdin \
      "$registry"

  local login_status=("${PIPESTATUS[@]}")

  if ((login_status[0] != 0 || login_status[1] != 0)); then
    echo "ERROR: ECR authentication failed." >&2
    return 1
  fi

  echo "Pushing Docker image to ECR..."

  docker push "$image_uri" || return 1

  echo "ECR image push completed."
}
