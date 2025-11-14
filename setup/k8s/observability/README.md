Observability Stack (Loki + Tempo + Prometheus + Grafana)

Overview
- Lightweight, single‑replica deployments intended for a 3‑node GKE e2-standard-2 cluster.
- Namespace: `observability`.
- Components:
  - Loki: log aggregation (HTTP: 3100)
  - Tempo: tracing backend with OTLP receivers (HTTP: 4318, gRPC: 4317, UI/API: 3200)
  - Prometheus: metrics scraping (pod annotations)
  - Grafana: dashboards with pre‑provisioned datasources (Prometheus, Loki, Tempo)
- Grafana exposed via existing NGINX Ingress at path `/grafana`.

Prerequisites
- gcloud, kubectl installed and pointing to your GKE cluster.
- NGINX Ingress controller installed (your setup-gke.sh already installs it).

Install
- Run:
  - `bash setup/k8s/observability/install.sh`
- The script creates `observability` namespace (if missing), applies ConfigMaps and Deployments, waits for rollouts, and applies the Ingress at `setup/k8s/observability/ingress.yaml`.

Uninstall
- Run:
  - `bash setup/k8s/observability/uninstall.sh`
- Optionally deletes the namespace when prompted.

Verify
- Pods and services:
  - `kubectl get pods,svc -n observability`
- Grafana access (via Ingress):
  - `http(s)://<your-ingress-host>/grafana` (admin/admin by default)
- In‑cluster endpoints:
  - Loki: `http://loki.observability.svc.cluster.local:3100`
  - Tempo: `http://tempo.observability.svc.cluster.local:3200` (OTLP: 4317 gRPC, 4318 HTTP)
  - Prometheus: `http://prometheus.observability.svc.cluster.local:9090`

Sending Data From Spring Boot
- Metrics (Prometheus): add pod annotations on your app Deployment in its namespace:
  - `prometheus.io/scrape: "true"`
  - `prometheus.io/port: "8080"`
  - `prometheus.io/path: "/actuator/prometheus"`
- Traces (OTLP): export OTLP to an Alloy sidecar running in the same pod, or directly to Tempo.
  - Direct to Tempo (simple): set `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://tempo.observability.svc.cluster.local:4318` and `OTEL_TRACES_EXPORTER=otlp` in your app container.
  - Sidecar Alloy (recommended when you add your app manifests): see template `setup/k8s/observability/sidecar-alloy-template.yaml` and copy it into your app namespace.
- Logs: for learning, you can start by exporting logs via Alloy sidecar to Loki, or keep basic stdout logs and view them in `kubectl logs`.

Common Ingress for App + Observability?
- Recommendation: use separate Ingress resources per namespace (one in your app namespace, one in `observability`).
  - NGINX Ingress will merge rules for the same host across namespaces, which keeps ownership and RBAC clear.
  - A single Ingress object that points to services in multiple namespaces is not supported by the Kubernetes Ingress API.
- Keep the existing `setup/k8s/observability-ingress.yaml` for Grafana at `/grafana`.
- Create an app Ingress in your app namespace with the same host and a different path, for example `/app`:

  Example (place under your app namespace repo/folder):
  
  apiVersion: networking.k8s.io/v1
  kind: Ingress
  metadata:
    name: app-ingress
    namespace: your-app-namespace
    annotations:
      kubernetes.io/ingress.class: nginx
  spec:
    rules:
      - host: <your-hostname>
        http:
          paths:
            - path: /app
              pathType: Prefix
              backend:
                service:
                  name: your-app-service
                  port:
                    number: 80

Notes
- Everything is single‑replica and uses emptyDir/filesystem storage for simplicity. For production, run HA replicas and durable storage.
- Change Grafana admin credentials in `setup/k8s/observability/grafana.yaml` if desired.
- Adjust resource requests/limits if your nodes are under pressure.
