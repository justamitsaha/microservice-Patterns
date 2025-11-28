# Microservice Stack on GKE (namespace: microservice)

## Overview
Deploys Spring Boot microservices and a web app:
- **customerService** (Spring Boot)
- **reactiveOrderService** (Spring Boot)
- **discoveryService** (Eureka server)
- **gatewayService** (API gateway)
- **configService** (Spring Cloud Config Server)
- **webapp** (frontend)

Additional components:
- MySQL (single instance) for customerService and reactiveOrderService
- Alloy sidecar in each Spring Boot Pod to forward traces/logs to your observability stack (Tempo/Loki)
- NGINX Ingress for external access with path-based routing

## Prerequisites
- Build and push container images for each app to a registry accessible from GKE
- Observability namespace installed (Tempo/Loki/Prometheus/Grafana)
- NGINX Ingress controller running (installed via `setup-gke.sh`)

## Install (step-by-step)

1. **Build & push all images** (skip if already pushed):
   ```bash
   REPO=<dockerhub-user> VERSION=v1 bash build-and-push.sh
   ```

2. **Run the installer** from repo root (sets up ConfigMaps, MySQL, Deployments, Ingress):
   ```bash
   IMAGE_REPO=<dockerhub-user> IMAGE_VERSION=v1 bash setup/k8s/microservice/install.sh
   ```

3. **Verify ConfigMaps** (especially `app-config` from `configService/src/main/resources/config`):
   ```bash
   kubectl get configmap -n microservice
   ```

4. **Watch pods start**:
   ```bash
   kubectl get pods -n microservice -w
   ```

5. **Verify ingress and services**:
   ```bash
   kubectl get ingress -n microservice
   kubectl get svc -n microservice
   ```

6. **Update config files**: If you modify files under `configService/src/main/resources/config`, rerun step 2 to recreate the `app-config` ConfigMap.

## Manual Setup (alternative to install.sh)

Run these commands from repo root in order:

1. **Create namespace**:
   ```bash
   kubectl create namespace microservice 2>/dev/null || true
   ```

2. **Apply network policy and Kafka topic**:
   ```bash
   kubectl apply -f setup/k8s/microservice/kafka-allow-from-microservice.yaml
   kubectl apply -f setup/k8s/microservice/config-bus-topic.yaml
   ```

3. **Create shared ConfigMaps**:
   ```bash
   kubectl apply -f setup/k8s/microservice/config-map/ -n microservice
   ```

4. **Deploy MySQL database**:
   ```bash
   kubectl apply -f setup/k8s/microservice/deployment/mysql.yaml -n microservice
   ```

5. **Deploy services** (set IMAGE_REPO/IMAGE_VERSION first):
   ```bash
   export IMAGE_REPO=justamitsaha
   export IMAGE_VERSION=v1
   envsubst < setup/k8s/microservice/deployment/configservice.yaml | kubectl apply -n microservice -f -
   envsubst < setup/k8s/microservice/deployment/discovery.yaml | kubectl apply -n microservice -f -
   envsubst < setup/k8s/microservice/deployment/gateway.yaml | kubectl apply -n microservice -f -
   envsubst < setup/k8s/microservice/deployment/customer.yaml | kubectl apply -n microservice -f -
   envsubst < setup/k8s/microservice/deployment/reactive-order.yaml | kubectl apply -n microservice -f -
   envsubst < setup/k8s/microservice/deployment/webapp.yaml | kubectl apply -n microservice -f -
   ```

6. **Create Ingress**:
   ```bash
   kubectl apply -f setup/k8s/microservice/ingress.yaml -n microservice
   ```

7. **Verify deployment** following steps 4-5 from the Install section above.

## Uninstall
```bash
bash setup/k8s/microservice/uninstall.sh
```
You'll be prompted whether to delete the `microservice` namespace.

## Ingress Paths
- `/` → webapp (port 80)
- `/api/customer` → customerService (8081)
- `/api/order` → reactiveOrderService (8080)
- `/gateway` → gatewayService (8085)
- `/eureka` → discoveryService (8761)
- `/config` → configService (8888)

## Config Server and Profiles
- Apps start with `SPRING_PROFILES_ACTIVE=gcp` (set via ConfigMap environment variables)
- Config Server URI: `http://configservice.microservice.svc.cluster.local:8888`
- Configuration sourced from `configmap-app-settings.yaml` and `app-config` ConfigMap (created from configService resources)

## Tracing/Logs Sidecar (Alloy)
- Alloy sidecar exposes OTLP on `localhost:4317/4318`
- Apps export OTLP to `http://localhost:4318`
- Alloy forwards:
  - Traces to `tempo.observability.svc.cluster.local:4317`
  - Logs to `loki.observability.svc.cluster.local:3100`

## Prometheus Metrics
Each Deployment includes annotations for Prometheus to scrape `/actuator/prometheus` on the service's container port.

## Container Images
- Manifests use `$IMAGE_REPO/new-ms-<service>:$IMAGE_VERSION` pattern (matches `build-and-push.sh` outputs)
- Override with: `IMAGE_REPO=<repo> IMAGE_VERSION=<tag>` when running install.sh