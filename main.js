/* ===========================
   Configuration: URLs + Commands
   =========================== */

/* ===========================================
   URL Generator
=========================================== */
const URL_MAP = {
    "Web App": "/web",
    "Config Server Actuator": "/config/actuator/health",
    "Config for Order API ": "/config/order-service/dev",
    "Config for Customer API": "/config/customer-service/default/main",
    "Config for Gateway": "/config/gateway-service/default/main",
    "Config for Discovery": "/config/discovery-service/default/main",
    "Eureka Server": "/eureka",
    "Eureka Actuator:": "/eureka/actuator/health",
    "Gateway Actuator": "/gateway",
    "Customer Actuator": "/api/customer/actuator/health",
    "Customer Swagger": "/api/customer/swagger-ui/webjars/swagger-ui/index.html",
    "Order Service": "/api/order"
};

/* ===========================================
   Commands rendering (compact)
   NOW USING ONLY STRINGS (NO LABELS)
=========================================== */
const COMMAND_SECTIONS = [
    {
        "title": "App Specific changes",
        "description": "Essential troubleshooting commands",
        "commands": {
            "cd /c/Amit/Work/code/Java/microservice/microservice-Patterns": "Go to project root",
            "export IMAGE_REPO=justamitsaha": "Set Docker image repo",
            "export IMAGE_VERSION=v1": "Set version tag",
            "envsubst < setup/k8s/microservice/deployment/configservice.yaml | kubectl apply -n microservice -f -": "Apply Config Service deployment",
            "envsubst < setup/k8s/microservice/deployment/discovery.yaml    | kubectl apply -n microservice -f -": "Apply Discovery Service deployment",
            "envsubst < setup/k8s/microservice/deployment/gateway.yaml      | kubectl apply -n microservice -f -": "Apply API Gateway deployment",
            "envsubst < setup/k8s/microservice/deployment/customer.yaml     | kubectl apply -n microservice -f -": "Apply Customer MS deployment",
            "envsubst < setup/k8s/microservice/deployment/reactive-order.yaml | kubectl apply -n microservice -f -": "Apply Reactive Order MS deployment",
            "envsubst < setup/k8s/microservice/deployment/webapp.yaml       | kubectl apply -n microservice -f -": "Apply Web App deployment",
            "kubectl apply -f setup/k8s/microservice/ingress -n microservice": "Apply Ingress setup",
            "curl -X POST http://localhost:8080/actuator/busrefresh": "Trigger config refresh via Bus"
        }
    },

    {
        "title": "Kubernetes Basics",
        "description": "Core kubectl operations",
        "commands": {
            "kubectl get ns": "List all namespaces",
            "kubectl get all -n microservice": "List all resources in microservice namespace",
            "kubectl get pods -A": "List all pods in all namespaces",
            "kubectl get svc -A": "List all services in all namespaces",
            "kubectl describe pod POD_NAME -n NAMESPACE": "Describe a specific pod",
            "kubectl delete ingress --all -n microservice": "Delete all ingresses in microservice namespace"
        }
    },

    {
        "title": "Port Forwarding",
        "description": "Access internal services locally",
        "commands": {
            "kubectl port-forward svc/mysql 3307:3306 -n microservice": "Forward MySQL to localhost:3307",
            "kubectl port-forward svc/redis 6380:6379 -n microservice": "Forward Redis to localhost:6380"
        }
    },

    {
        "title": "Docker Commands",
        "description": "Useful for debugging containers",
        "commands": {
            "docker logs -f container-name": "Tail container logs",
            "docker exec -it container-name sh": "Enter running container shell"
        }
    }
];

