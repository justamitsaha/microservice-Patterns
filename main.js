/* ===========================
   Configuration: URLs + Commands
   =========================== */

/* ===========================================
   URL Generator
=========================================== */
const URL_MAP = {
    "Config Server Actuator": "/config/actuator/health",
    "Config for Order API ": "/config/order-service/dev",
    "Config for Customer API": "/config/customer-service/default/main",
    "Config for Gateway": "/config/gateway-service/default/main",
    "Config for Discovery": "/config/discovery-service/default/main",
    "Eureka Dashboard": "/discovery",
    "Eureka Actuator:": "/discovery/actuator/health",
    "Gateway Actuator": "/gateway/actuator/health",
    "Customer Actuator": "/api/customer/actuator/health",
    "Customer Swagger": "/api/customer/swagger-ui/webjars/swagger-ui/index.html",
    "Order Service": "/api/order/actuator/health",
    "Order Swagger": "/api/order/swagger-ui/index.html",
    "Web App": "/web",
    "Grafana": "http://35.226.236.202/grafana/login"
};

/* ===========================================
   Commands rendering (compact)
   NOW USING ONLY STRINGS (NO LABELS)
=========================================== */
const COMMAND_SECTIONS = [
    {
        "title": "Set up Microservice on Kubernetes",
        "commands": {
            "cd /c/Amit/Work/code/Java/microservice/microservice-Patterns": "Go to project root",
            "kubectl delete namespace microservice": "Delete existing microservice namespace",
            "kubectl create namespace microservice 2>/dev/null || true": "Create microservice namespace",
            "kubectl apply -f setup/k8s/microservice/kafka-allow-from-microservice.yaml": "Allow microservice namespace to access Kafka",
            "kubectl apply -f setup/k8s/microservice/config-bus-topic.yaml": "Create Config Bus topic",
            "kubectl apply -f setup/k8s/microservice/config-map/ -n microservice": "Apply Config Maps",
            "kubectl apply -f setup/k8s/microservice/deployment/mysql.yaml -n microservice": "Apply MySQL deployment",
            "kubectl apply -f setup/k8s/microservice/deployment/create-tables-job.yaml -n microservice": "Apply DB table creation job",
            "kubectl exec -it deploy/mysql -n microservice -- mysql -uappuser -pappPass123 -e \"SHOW TABLES IN amit;\"": "Verify DB tables created",
            "export IMAGE_REPO=justamitsaha": "Set Docker image repo",
            "export IMAGE_VERSION=v1": "Set version tag",
            "envsubst < setup/k8s/microservice/deployment/configservice.yaml | kubectl apply -n microservice -f -": "Apply Config Service",
            "envsubst < setup/k8s/microservice/deployment/discovery.yaml| kubectl apply -n microservice -f -": "Apply Discovery Service",
            "envsubst < setup/k8s/microservice/deployment/gateway.yaml | kubectl apply -n microservice -f -": "Apply API Gateway",
            "envsubst < setup/k8s/microservice/deployment/customer.yaml | kubectl apply -n microservice -f -": "Apply Customer MS",
            "envsubst < setup/k8s/microservice/deployment/reactive-order.yaml | kubectl apply -n microservice -f -": "Apply Reactive Order MS",
            "envsubst < setup/k8s/microservice/deployment/webapp.yaml | kubectl apply -n microservice -f -": "Apply Web App",
            "kubectl apply -f setup/k8s/microservice/ingress -n microservice": "Apply Ingress setup"
        }
    },
    {
        "title": "Cluster State & Health (Triaging)",
        "description": "First steps when something goes wrong",
        "commands": {
            "kubectl get all -n microservice": "Overview of all resources",
            "kubectl get svc -n microservice": "List Services and Cluster IPs",
            "kubectl get deployment -n microservice": "Check Deployment status (Replicas)",
            "kubectl get rs -n microservice": "Check ReplicaSets (New/Old)",
            "kubectl get pods -n microservice -o wide": "List pods with IPs and Node assignment",
            "kubectl get events -n microservice --sort-by='.lastTimestamp'": "List recent Cluster Events (Errors/Warnings)",
            "kubectl top pods -n microservice": "Check Pod CPU and Memory usage",
            "kubectl get endpoints -n microservice": "Verify Services have active Pod IPs attached"
        }
    },
    {
        "title": "Deep Dive Debugging",
        "description": "Investigating specific pod issues",
        "commands": {
            "kubectl logs POD_NAME -n microservice": "View live logs",
            "kubectl logs POD_NAME -n microservice --previous": "View logs from the CRASHED instance",
            "kubectl describe pod POD_NAME -n microservice": "Detailed Pod status (Why is it pending?)",
            "kubectl exec -it POD_NAME -n microservice -- sh": "Enter Pod Shell",
            "kubectl debug -it POD_NAME -n microservice --image=busybox --target=mysql": "Attach Ephemeral Debug Container (Advanced)"
        }
    },
    {
        "title": "Deployments & Roll outs",
        "commands": {
            "kubectl rollout restart deployment -n microservice": "Restart ALL microservices",
            "kubectl rollout restart deployment/configservice -n microservice": "Restart only Config Service",
            "kubectl rollout status deployment/gateway -n microservice": "Check Gateway rollout status",
            "kubectl rollout history deployment/customer -n microservice": "View Customer MS rollout history",
            "kubectl rollout undo deployment/reactiveorderservice -n microservice": "Rollback Order MS to previous version",
            "kubectl get pods -n microservice --show-labels": "List pods with labels for verification"
        }
    },
    {
        "title": "Port Forwarding & External Access",
        "description": "Access internal services locally",
        "commands": {
            "kubectl port-forward svc/gateway 8080:8080 -n microservice": "Expose Gateway to localhost:8080",
            "kubectl port-forward svc/mysql 3307:3306 -n microservice": "Expose MySQL to localhost:3307",
            "curl -X POST http://localhost:8888/actuator/busrefresh": "Trigger Config Bus Refresh"
        }
    },
    {
        "title": "Configuration Verification",
        "commands": {
            "kubectl get configmap -n microservice": "List ConfigMaps",
            "kubectl get secret -n microservice": "List Secrets",
            "kubectl get configmap config-repo -n microservice -o yaml": "View actual injected ConfigMap YAML"
        }
    }
];

