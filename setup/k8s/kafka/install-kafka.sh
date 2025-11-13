#!/usr/bin/env bash
set -euo pipefail

# One-shot installer for Kafka (Strimzi, KRaft 4.0.0) + Apicurio Schema Registry on GKE
# Safe to run from Git Bash on Windows.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Align defaults with setup/k8s/setup-gke.sh
: "${PROJECT_ID:=$(gcloud config get-value project 2>/dev/null || echo)}"
: "${REGION:=us-central1}"
: "${ZONE:=us-central1-a}"
: "${CLUSTER_NAME:=amit-cluster}"
: "${NAMESPACE:=kafka}"
: "${STRIMZI_RELEASE:=strimzi-kafka-operator}"

echo "Project:       ${PROJECT_ID}"
echo "Region/Zone:   ${REGION} / ${ZONE}"
echo "Cluster:       ${CLUSTER_NAME}"
echo "Namespace:     ${NAMESPACE}"
echo "----------------------------------------"

need() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing dependency: $1"; exit 1; }
}

wait_namespace_active() {
  local ns="$1"
  echo "Waiting for namespace ${ns} to be Active..."
  for i in {1..60}; do
    phase=$(kubectl get ns "$ns" -o jsonpath='{.status.phase}' 2>/dev/null || echo "")
    if [[ "$phase" == "Active" ]]; then
      return 0
    fi
    sleep 2
  done
  echo "Warning: namespace ${ns} not Active after wait; continuing..."
}

cleanup_strimzi_rolebindings() {
  local ns="$1"
  echo "Ensuring required RoleBindings do not exist in ${ns}..."
  for rb in \
    strimzi-cluster-operator-watched \
    strimzi-cluster-operator \
    strimzi-cluster-operator-entity-operator-delegation; do
    if kubectl -n "$ns" get rolebinding "$rb" >/dev/null 2>&1; then
      kubectl -n "$ns" delete rolebinding "$rb" --wait --ignore-not-found || true
    fi
  done
}

echo "Checking required CLIs..."
need gcloud
need kubectl
need helm

echo "Connecting kubectl to GKE cluster..."
gcloud container clusters get-credentials "${CLUSTER_NAME}" --zone "${ZONE}" ${PROJECT_ID:+--project ${PROJECT_ID}}

echo "Creating namespace ${NAMESPACE} (if missing)..."
kubectl get ns "${NAMESPACE}" >/dev/null 2>&1 || kubectl create namespace "${NAMESPACE}"
wait_namespace_active "${NAMESPACE}"

echo "Adding Strimzi Helm repo..."
helm repo add strimzi https://strimzi.io/charts/ >/dev/null 2>&1 || true
helm repo update >/dev/null 2>&1 || true

echo "Ensuring Strimzi operator is present (watching only namespace: ${NAMESPACE})..."
if kubectl -n "${NAMESPACE}" get deploy/strimzi-cluster-operator >/dev/null 2>&1; then
  echo "Strimzi operator already present. Skipping Helm install."
else
  echo "Installing Strimzi operator..."
  cleanup_strimzi_rolebindings "${NAMESPACE}"
  set +e
  helm upgrade --install "${STRIMZI_RELEASE}" strimzi/strimzi-kafka-operator \
    --namespace "${NAMESPACE}" \
    --set watchNamespaces[0]="${NAMESPACE}" \
    --wait --timeout 10m
  code=$?
  if [[ $code -ne 0 ]]; then
    echo "Helm install failed (code=$code). Retrying after cleaning RoleBindings..."
    cleanup_strimzi_rolebindings "${NAMESPACE}"
    helm upgrade --install "${STRIMZI_RELEASE}" strimzi/strimzi-kafka-operator \
      --namespace "${NAMESPACE}" \
      --set watchNamespaces[0]="${NAMESPACE}" \
      --wait --timeout 10m || exit 1
  fi
  set -e
fi

echo "Waiting for Strimzi operator rollout..."
kubectl -n "${NAMESPACE}" rollout status deploy/strimzi-cluster-operator --timeout=5m

echo "Applying Kafka cluster (KRaft, 3 replicas, ephemeral storage)..."
kubectl apply -n "${NAMESPACE}" -f "${SCRIPT_DIR}/strimzi/kafka.yaml"

echo "Waiting for Kafka to be Ready (this can take several minutes)..."
kubectl wait kafka/my-cluster -n "${NAMESPACE}" --for=condition=Ready --timeout=15m

echo "Deploying Apicurio Schema Registry (KafkaSQL)..."
kubectl apply -n "${NAMESPACE}" -f "${SCRIPT_DIR}/strimzi/schema-registry-apicurio.yaml"

echo "Waiting for Schema Registry to become ready..."
kubectl rollout status -n "${NAMESPACE}" deploy/schema-registry --timeout=5m

echo "----------------------------------------"
echo "Deployed successfully. Connection info:"
echo "Kafka bootstrap: my-cluster-kafka-bootstrap.${NAMESPACE}.svc.cluster.local:9092"
echo "Schema Registry: http://schema-registry.${NAMESPACE}.svc.cluster.local:8081"

echo "Quick verify (Schema Registry groups):"
kubectl run --rm -i --tty curl --image=curlimages/curl:8.8.0 -n "${NAMESPACE}" --restart=Never -- \
  curl -s http://schema-registry.${NAMESPACE}.svc.cluster.local:8081/apis/registry/v2/groups || true

echo "Done."
