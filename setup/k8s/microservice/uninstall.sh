#!/usr/bin/env bash
set -euo pipefail

NS=${NAMESPACE:-microservice}

echo "Namespace: ${NS}"
BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "======================================="
echo "Deleting Ingress..."
echo "======================================="
kubectl -n "${NS}" delete -f "${BASE_DIR}/ingress/" --ignore-not-found

echo "======================================="
echo "Deleting Deployments & Services..."
echo "======================================="
for f in \
  "deployment/configservice.yaml" \
  "deployment/discovery.yaml" \
  "deployment/gateway.yaml" \
  "deployment/customer.yaml" \
  "deployment/reactive-order.yaml" \
  "deployment/webapp.yaml" \
  "deployment/mysql.yaml" \
  "deployment/create-tables-job.yaml"
do
  FILE="${BASE_DIR}/$f"
  if [[ -f "$FILE" ]]; then
    echo "Deleting $FILE ..."
    kubectl -n "${NS}" delete -f "$FILE" --ignore-not-found || true
  fi
done

echo "======================================="
echo "Deleting ConfigMaps..."
echo "======================================="
# All config maps under config-map folder
for cm in \
  microservice-app-settings \
  configservice-config \
  customer-service-config \
  discovery-service-config \
  gateway-service-config \
  order-service-config \
  alloy-config \
  mysql-initdb
do
  kubectl -n "${NS}" delete configmap "$cm" --ignore-not-found || true
done

echo "======================================="
echo "Deleting Secrets (optional)..."
echo "======================================="
kubectl -n "${NS}" delete secret mysql-secret --ignore-not-found || true

echo ""
echo "Optionally delete namespace ${NS}? (y/N)"
read -r ans
if [[ "${ans}" =~ ^[Yy]$ ]]; then
  kubectl delete namespace "${NS}" --wait
fi

echo "Uninstall complete."
