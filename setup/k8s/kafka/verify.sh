#!/usr/bin/env bash
set -euo pipefail

NAMESPACE=${NAMESPACE:-kafka}

echo "Kafka pods:"
kubectl get pods -n "${NAMESPACE}" -l app.kubernetes.io/name=kafka -o name

POD=$(kubectl get pods -n "${NAMESPACE}" -l app.kubernetes.io/name=kafka -o jsonpath='{.items[0].metadata.name}')
echo "Using pod: ${POD}"

echo "Creating test topic 'demo-topic' (ignore if exists)..."
kubectl exec -n "${NAMESPACE}" -it "${POD}" -- bash -lc \
  "/opt/bitnami/kafka/bin/kafka-topics.sh --create --if-not-exists --topic demo-topic --replication-factor 3 --partitions 3 --bootstrap-server kafka:9092"

echo "Listing topics..."
kubectl exec -n "${NAMESPACE}" -it "${POD}" -- bash -lc \
  "/opt/bitnami/kafka/bin/kafka-topics.sh --list --bootstrap-server kafka:9092"

echo "Checking Schema Registry subjects..."
kubectl run --rm -i --tty curl --image=curlimages/curl:8.8.0 -n "${NAMESPACE}" --restart=Never -- \
  curl -s http://schema-registry.${NAMESPACE}.svc.cluster.local:8081/subjects || true

echo "Verify complete."

