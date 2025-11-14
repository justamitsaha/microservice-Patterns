#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NS=${NAMESPACE:-microservice}
IMAGE_REPO=${IMAGE_REPO:-${IMAGE_PREFIX:-justamitsaha}}
IMAGE_VERSION=${IMAGE_VERSION:-v1}

echo "Namespace: ${NS}"
kubectl get ns "${NS}" >/dev/null 2>&1 || kubectl create namespace "${NS}"

echo "Creating/updating ConfigMap from configService resources..."
CFG_DIR="${SCRIPT_DIR}/../../configService/src/main/resources/config"
if [ -d "${CFG_DIR}" ]; then
  kubectl -n "${NS}" create configmap app-config --from-file="${CFG_DIR}" \
    --dry-run=client -o yaml | kubectl apply -f -
else
  echo "Warning: ${CFG_DIR} not found. Skipping app-config ConfigMap."
fi

echo "Creating/updating Alloy sidecar ConfigMap..."
envsubst < "${SCRIPT_DIR}/alloy-config.yaml" | kubectl -n "${NS}" apply -f -

echo "Applying MySQL, apps, and services..."
IMAGE_REPO=${IMAGE_REPO} IMAGE_VERSION=${IMAGE_VERSION} envsubst < "${SCRIPT_DIR}/mysql.yaml" | kubectl -n "${NS}" apply -f -
IMAGE_REPO=${IMAGE_REPO} IMAGE_VERSION=${IMAGE_VERSION} envsubst < "${SCRIPT_DIR}/configservice.yaml" | kubectl -n "${NS}" apply -f -
IMAGE_REPO=${IMAGE_REPO} IMAGE_VERSION=${IMAGE_VERSION} envsubst < "${SCRIPT_DIR}/discovery.yaml" | kubectl -n "${NS}" apply -f -
IMAGE_REPO=${IMAGE_REPO} IMAGE_VERSION=${IMAGE_VERSION} envsubst < "${SCRIPT_DIR}/gateway.yaml" | kubectl -n "${NS}" apply -f -
IMAGE_REPO=${IMAGE_REPO} IMAGE_VERSION=${IMAGE_VERSION} envsubst < "${SCRIPT_DIR}/customer.yaml" | kubectl -n "${NS}" apply -f -
IMAGE_REPO=${IMAGE_REPO} IMAGE_VERSION=${IMAGE_VERSION} envsubst < "${SCRIPT_DIR}/reactive-order.yaml" | kubectl -n "${NS}" apply -f -
IMAGE_REPO=${IMAGE_REPO} IMAGE_VERSION=${IMAGE_VERSION} envsubst < "${SCRIPT_DIR}/webapp.yaml" | kubectl -n "${NS}" apply -f -

echo "Applying Ingress..."
kubectl -n "${NS}" apply -f "${SCRIPT_DIR}/ingress.yaml"

echo "Waiting for core deployments (configservice, discovery, gateway)..."
kubectl -n "${NS}" rollout status deploy/configservice --timeout=10m || true
kubectl -n "${NS}" rollout status deploy/discovery --timeout=10m || true
kubectl -n "${NS}" rollout status deploy/gateway --timeout=10m || true

echo "Done. Update images by setting IMAGE_REPO and IMAGE_VERSION."
