#!/usr/bin/env bash
set -euo pipefail

# Uninstall Kafka (Strimzi) + Apicurio Schema Registry

: "${NAMESPACE:=kafka}"
: "${STRIMZI_RELEASE:=strimzi-kafka-operator}"

echo "Namespace: ${NAMESPACE}"

echo "Deleting Schema Registry..."
kubectl delete -n "${NAMESPACE}" -f "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/strimzi/schema-registry-apicurio.yaml" --ignore-not-found

echo "Deleting Kafka CRs (Kafka and KafkaNodePool)..."
kubectl delete -n "${NAMESPACE}" -f "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/strimzi/kafka.yaml" --ignore-not-found || true

echo "Uninstalling Strimzi operator..."
helm uninstall "${STRIMZI_RELEASE}" -n "${NAMESPACE}" || true

echo "Optionally delete namespace ${NAMESPACE} (y/N)?"
read -r ans
if [[ "${ans}" =~ ^[Yy]$ ]]; then
  kubectl delete namespace "${NAMESPACE}" --wait
fi

echo "Done."

