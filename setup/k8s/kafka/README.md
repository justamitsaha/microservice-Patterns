Kafka on GKE (3 Brokers, KRaft) with Schema Registry

Overview
- Deploys a Kafka cluster (3 brokers, KRaft) via Strimzi and Apicurio Schema Registry in namespace `kafka`.
- Intended for in-cluster usage by Spring Boot apps running on GKE.

Requirements
- gcloud, kubectl, and helm installed locally and authenticated to your GCP project.
- GKE cluster already created (e.g., via `setup/k8s/setup-gke.sh`).

Quick Start
- Install (Strimzi Kafka + Apicurio Registry):
  - `bash setup/k8s/kafka/install-kafka.sh`
- Uninstall:
  - `bash setup/k8s/kafka/uninstall-kafka.sh`

Connection for Apps (inside cluster)
- Kafka Bootstrap: `my-cluster-kafka-bootstrap.kafka.svc.cluster.local:9092`
- Schema Registry: `http://schema-registry.kafka.svc.cluster.local:8081`

Spring Boot examples (application.yaml)
- `spring.kafka.bootstrap-servers: my-cluster-kafka-bootstrap.kafka.svc.cluster.local:9092`
- For serializers using Confluent Schema Registry API, Apicurio provides compatible endpoints (e.g. `/apis/ccompat/v6`). Point your client to the URL above.

Notes
- This setup is optimized for in-cluster connectivity. No external listeners are exposed by default.
- If you need external access, consider adding a separate listener or port-forwarding.
