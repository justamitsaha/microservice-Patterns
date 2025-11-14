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
- Set an image prefix for your registry (examples below). Then run:
  - `IMAGE_PREFIX=gcr.io/<project>/microservices bash setup/k8s/microservice/install.sh`
  - or `IMAGE_PREFIX=<your-dockerhub-username> bash setup/k8s/microservice/install.sh`

Uninstall
- Run: `bash setup/k8s/microservice/uninstall.sh`
- Optionally deletes the `microservice` namespace when prompted.

Ingress paths (example)
- `/` → webapp (port 80)
- `/api/customer` → customerService (8080)
- `/api/order` → reactiveOrderService (8080)
- `/gateway` → gatewayService (8080)
- `/eureka` → discoveryService (8761)
- `/config` → configService (8888)

Config Server and Profiles
- Apps are started with `SPRING_PROFILES_ACTIVE=gcp`.
- Config Server URI: `http://configservice.microservice.svc.cluster.local:8888`
- The install script creates a ConfigMap from `configService/src/main/resources/config`.

Tracing/Logs Sidecar (Alloy)
- Alloy sidecar exposes OTLP on localhost:4317/4318; apps export OTLP to `http://localhost:4318`.
- Alloy forwards traces to `tempo.observability.svc.cluster.local:4317` and logs to `loki.observability.svc.cluster.local:3100`.

Prometheus Metrics
- Each Deployment has annotations for Prometheus to scrape `/actuator/prometheus` on container port 8080.

Images
- The manifests use `${IMAGE_PREFIX}` with reasonable image names, e.g. `${IMAGE_PREFIX}/customer-service:latest`.
- Override with `IMAGE_PREFIX=...` when running install.sh.

