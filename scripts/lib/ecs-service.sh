#!/usr/bin/env bash

start_ecs_service() {
  aws ecs update-service \
    --profile "$AWS_PROFILE" \
    --region "$AWS_REGION" \
    --cluster "equipment-inspection-cluster" \
    --service "equipment-inspection-service" \
    --desired-count 1 \
    --output json >/dev/null
}

wait_for_ecs_service_stable() {
  aws ecs wait services-stable \
    --profile "$AWS_PROFILE" \
    --region "$AWS_REGION" \
    --cluster "equipment-inspection-cluster" \
    --services "equipment-inspection-service"
}
