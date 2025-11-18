Microservice Stack on GKE (namespace: microservice)

Overview
- Deploys Spring Boot microservices and a web app:
  - customerService (Spring Boot)
  - reactiveOrderService (Spring Boot)
  - discoveryService (Eureka server)
  - gatewayService (API gateway)
  - configService (Spring Cloud Config Server)
  - webapp (frontend)
- Adds MySQL (single instance) for customerService and reactiveOrderService
- Adds Alloy sidecar to each Spring Boot Pod to forward traces/logs to your observability stack (Tempo/Loki)
- Exposes services via NGINX Ingress with simple path routing

Prerequisites
- Build and push container images for each app to a registry you can pull from GKE.
- Observability namespace is installed (Tempo/Loki/Prometheus/Grafana).
- NGINX Ingress controller is running (your setup-gke.sh installs it).

Install
- Set an image repo/tag (defaults: `IMAGE_REPO=justamitsaha`, `IMAGE_VERSION=v1`). Then run:
  - `IMAGE_REPO=gcr.io/<project>/microservices IMAGE_VERSION=v1 bash setup/k8s/microservice/install.sh`
  - or `IMAGE_REPO=<dockerhub-user> IMAGE_VERSION=v2 bash setup/k8s/microservice/install.sh`
- The script applies `configmap-app-settings.yaml` (shared env defaults) followed by Config Server files and Deployments.

Uninstall
- Run: `bash setup/k8s/microservice/uninstall.sh`
- Optionally deletes the `microservice` namespace when prompted.

Ingress paths (example)
- `/` → webapp (port 80)
- `/api/customer` → customerService (8081)
- `/api/order` → reactiveOrderService (8080)
- `/gateway` → gatewayService (8085)
- `/eureka` → discoveryService (8761)
- `/config` → configService (8888)

Config Server and Profiles
- Apps are started with `SPRING_PROFILES_ACTIVE=gcp` (set via ConfigMap env vars).
- Config Server URI: `http://configservice.microservice.svc.cluster.local:8888`
- `configmap-app-settings.yaml` plus `app-config` ConfigMap (from configService resources) feed both in-jar properties and Config Server overrides.

Tracing/Logs Sidecar (Alloy)
- Alloy sidecar exposes OTLP on localhost:4317/4318; apps export OTLP to `http://localhost:4318`.
- Alloy forwards traces to `tempo.observability.svc.cluster.local:4317` and logs to `loki.observability.svc.cluster.local:3100`.

Prometheus Metrics
- Each Deployment has annotations for Prometheus to scrape `/actuator/prometheus` on the service’s container port.

Images
- The manifests use `$IMAGE_REPO/new-ms-<service>:$IMAGE_VERSION` to match `build-and-push.sh` outputs.
- Override via `IMAGE_REPO=<repo> IMAGE_VERSION=<tag>` when running install.sh.
