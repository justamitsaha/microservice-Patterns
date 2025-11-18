#!/usr/bin/env bash
set -euo pipefail

NS=${NAMESPACE:-microservice}

echo "Namespace: ${NS}"
echo "Deleting Ingress..."
kubectl -n "${NS}" delete -f "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/ingress.yaml" --ignore-not-found

echo "Deleting apps and services..."
for f in webapp.yaml reactive-order.yaml customer.yaml gateway.yaml discovery.yaml configservice.yaml mysql.yaml; do
  kubectl -n "${NS}" delete -f "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$f" --ignore-not-found || true
done

echo "Deleting ConfigMaps..."
kubectl -n "${NS}" delete configmap app-config --ignore-not-found || true
kubectl -n "${NS}" delete configmap alloy-config --ignore-not-found || true
kubectl -n "${NS}" delete configmap microservice-app-settings --ignore-not-found || true
kubectl -n "${NS}" delete configmap mysql-initdb --ignore-not-found || true

echo "Optionally delete namespace ${NS} (y/N)?"
read -r ans
if [[ "${ans}" =~ ^[Yy]$ ]]; then
  kubectl delete namespace "${NS}" --wait
fi

echo "Done."
