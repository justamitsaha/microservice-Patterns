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

Install (step-by-step)
1. From the repo root, build & push all images (optional if already pushed):
   ```bash
   REPO=<dockerhub-user> VERSION=v1 bash build-and-push.sh
   ```
2. Ensure you are still at repo root and run the installer (sets up ConfigMaps, MySQL, Deployments, Ingress):
   ```bash
   IMAGE_REPO=<dockerhub-user> IMAGE_VERSION=v1 bash setup/k8s/microservice/install.sh
   ```
3. Confirm the required ConfigMaps exist (especially `app-config` produced from `configService/src/main/resources/config`):
   ```bash
   kubectl get configmap -n microservice
   ```
4. Watch pods come up:
   ```bash
   kubectl get pods -n microservice -w
   ```
5. Verify ingress/service endpoints:
   ```bash
   kubectl get ingress -n microservice
   kubectl get svc -n microservice
   ```
6. If you update any config files under `configService/src/main/resources/config`, rerun step 2 so `app-config` is recreated.

Manual setup (skip install.sh)
Run these commands from repo root in order:
1. Create namespace (if missing):
   ```bash
   kubectl create namespace microservice 2>/dev/null || true
   ```
2. Shared ConfigMaps:
   ```bash
   kubectl apply -f setup/k8s/microservice/configmap-app-settings.yaml
   kubectl apply -f setup/k8s/microservice/mysql-initdb-configmap.yaml
   kubectl -n microservice create configmap app-config \
     --from-file=configService/src/main/resources/config/gcp \
     --dry-run=client -o yaml | kubectl apply -f -
   kubectl -n microservice create configmap alloy-config \
     --from-file=setup/k8s/microservice/alloy-config.yaml \
     --dry-run=client -o yaml | kubectl apply -f -

   bash kubectl apply -f setup/k8s/microservice/kafka-allow-from-microservice.yaml     
   ```

3. Database:
   ```bash
   kubectl apply -f setup/k8s/microservice/mysql.yaml -n microservice
   ```
4. Services (set IMAGE_REPO/IMAGE_VERSION first):
   ```bash
   export IMAGE_REPO=justamitsaha
   export IMAGE_VERSION=v1
   envsubst < setup/k8s/microservice/configservice.yaml | kubectl apply -n microservice -f -
   envsubst < setup/k8s/microservice/discovery.yaml    | kubectl apply -n microservice -f -
   envsubst < setup/k8s/microservice/gateway.yaml      | kubectl apply -n microservice -f -
   envsubst < setup/k8s/microservice/customer.yaml     | kubectl apply -n microservice -f -
   envsubst < setup/k8s/microservice/reactive-order.yaml | kubectl apply -n microservice -f -
   envsubst < setup/k8s/microservice/webapp.yaml       | kubectl apply -n microservice -f -
   ```
5. Ingress:
   ```bash
   kubectl apply -f setup/k8s/microservice/ingress.yaml -n microservice
   ```
6. Watch pods and verify like in steps 4–5 above.

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
