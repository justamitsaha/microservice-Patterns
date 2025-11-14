#!/usr/bin/env bash
set -euo pipefail

# Lightweight observability stack for learning on GKE:
# - Namespace: observability
# - Loki (logs), Tempo (traces), Prometheus (metrics), Grafana (UI + pre-provisioned datasources)
# - Exposes Grafana via Ingress (path /grafana)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NS=${NAMESPACE:-observability}

echo "Using namespace: ${NS}"
kubectl get ns "${NS}" >/dev/null 2>&1 || kubectl create namespace "${NS}"

echo "Applying configs (Loki/Tempo/Prometheus/Grafana datasources)..."
kubectl apply -n "${NS}" -f "${SCRIPT_DIR}/loki-config.yaml"
kubectl apply -n "${NS}" -f "${SCRIPT_DIR}/tempo-config.yaml"
kubectl apply -n "${NS}" -f "${SCRIPT_DIR}/prometheus-config.yaml"
kubectl apply -n "${NS}" -f "${SCRIPT_DIR}/grafana-datasources.yaml"

echo "Deploying services..."
kubectl apply -n "${NS}" -f "${SCRIPT_DIR}/loki.yaml"
kubectl apply -n "${NS}" -f "${SCRIPT_DIR}/tempo.yaml"
kubectl apply -n "${NS}" -f "${SCRIPT_DIR}/prometheus.yaml"
kubectl apply -n "${NS}" -f "${SCRIPT_DIR}/grafana.yaml"

echo "Waiting for deployments to be ready..."
kubectl rollout status -n "${NS}" deploy/loki --timeout=5m
kubectl rollout status -n "${NS}" deploy/tempo --timeout=5m
kubectl rollout status -n "${NS}" deploy/prometheus --timeout=5m
kubectl rollout status -n "${NS}" deploy/grafana --timeout=5m

echo "Applying Ingress for Grafana..."
kubectl apply -f "${SCRIPT_DIR}/ingress.yaml"

echo "Done. Endpoints in-cluster:"
echo "- Loki:       http://loki.${NS}.svc.cluster.local:3100"
echo "- Tempo:      http://tempo.${NS}.svc.cluster.local:3200"
echo "- Prometheus: http://prometheus.${NS}.svc.cluster.local:9090"
echo "- Grafana:    http(s)://<your-host>/grafana (via your existing Ingress)"
