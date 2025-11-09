# 0) Get kubeconfig for your cluster
gcloud container clusters get-credentials amit-cluster --zone us-central1-a

# 1) Namespaces
kubectl apply -f setup/k8s/namespaces.yaml

# 2) Helm repos
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

# 3) Zookeeper + Kafka (Bitnami)
helm upgrade --install zookeeper bitnami/zookeeper \
  -n observability -f setup/k8s/kafka/values-zookeeper.yaml

helm upgrade --install kafka bitnami/kafka \
  -n observability -f setup/k8s/kafka/values-kafka.yaml

# 4) Tempo
kubectl apply -f setup/k8s/tempo/tempo-pvc.yaml
kubectl apply -f setup/k8s/tempo/tempo-config.yaml
kubectl apply -f setup/k8s/tempo/tempo-deploy.yaml

# 5) OTEL Collector
kubectl apply -f setup/k8s/otel/otel-collector-config.yaml
kubectl apply -f setup/k8s/otel/otel-collector-deploy.yaml

# 6) Prometheus (Helm)
helm upgrade --install prometheus prometheus-community/prometheus \
  -n observability -f setup/k8s/prometheus/values-prometheus.yaml

# 7) Grafana (Helm)
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update
helm upgrade --install grafana grafana/grafana \
  -n observability -f setup/k8s/grafana/values-grafana.yaml

# 8) Load your Grafana dashboards (ConfigMaps with sidecar label)
kubectl create configmap grafana-dashboard-http \
  -n observability --from-file=observability/grafana/dashboards/http-overview.json \
  --dry-run=client -o yaml \
  | kubectl apply -f -

kubectl label configmap grafana-dashboard-http \
  -n observability grafana_dashboard=1 --overwrite


kubectl create configmap grafana-dashboard-resilience4j \
  -n observability --from-file=observability/grafana/dashboards/resilience4j.json \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl label configmap grafana-dashboard-resilience4j \
  -n observability grafana_dashboard=1 --overwrite



kubectl create configmap grafana-dashboard-tracing \
  -n observability --from-file=observability/grafana/dashboards/tracing-overview.json \
  --dry-run=client -o yaml | kubectl apply -f -

kubectl label configmap grafana-dashboard-tracing \
  -n observability grafana_dashboard=1 --overwrite

#verify
kubectl get configmaps -n observability


kubectl apply -f setup/k8s/schema-registry/schema-registry-deploy.yaml
kubectl apply -f setup/k8s/schema-registry/schema-registry-svc.yaml

kubectl -n observability get pods -l app=schema-registry
kubectl -n observability get svc schema-registry

Port-forward for local testing:

kubectl -n observability port-forward svc/schema-registry 8081:8081
# test:
curl http://localhost:8081/subjects