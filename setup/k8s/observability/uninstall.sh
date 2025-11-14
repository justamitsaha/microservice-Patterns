#!/usr/bin/env bash
set -euo pipefail

NS=${NAMESPACE:-observability}

echo "Namespace: ${NS}"
echo "Deleting Ingress..."
kubectl delete -f "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/ingress.yaml" --ignore-not-found

echo "Deleting deployments/services/configmaps..."
kubectl delete -n "${NS}" -f "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/grafana.yaml" --ignore-not-found
kubectl delete -n "${NS}" -f "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/prometheus.yaml" --ignore-not-found
kubectl delete -n "${NS}" -f "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/tempo.yaml" --ignore-not-found
kubectl delete -n "${NS}" -f "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/loki.yaml" --ignore-not-found
kubectl delete -n "${NS}" -f "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/grafana-datasources.yaml" --ignore-not-found
kubectl delete -n "${NS}" -f "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/prometheus-config.yaml" --ignore-not-found
kubectl delete -n "${NS}" -f "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/tempo-config.yaml" --ignore-not-found
kubectl delete -n "${NS}" -f "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/loki-config.yaml" --ignore-not-found

echo "Optionally delete namespace ${NS} (y/N)?"
read -r ans
if [[ "${ans}" =~ ^[Yy]$ ]]; then
  kubectl delete namespace "${NS}" --wait
fi

echo "Done."
