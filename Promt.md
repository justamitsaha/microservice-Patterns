# Kubernetes Ingress Routing Issue

## 1. ROLE & TECH STACK
**Role:** Act as a Senior Expert in Spring, Spring Boot, and Java web development.

**Current Stack:**
- **Language/Version:** Java 21
- **Framework/Library:** Spring Boot 3.5/3.6, Angular
- **Infrastructure:** GCP, Docker, Kubernetes

## 2. CONTEXT & OBJECTIVE

**The Goal:** Set up ingress routing for Angular web app at `/web` and Spring Boot Eureka server at `/discovery`

**The Problem:** 
- Web App showing nginx 404 at `http://35.226.236.202/web`
- Eureka Dashboard showing 404 at `http://35.226.236.202/discovery`
- Eureka Actuator showing 404 at `http://35.226.236.202/discovery/actuator/health`

**Specific Question:** Need to fix the routing configuration

## 3. CODE ARTIFACTS

Please review the following code/configuration files.

### File: webapp-ingress.yaml
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: webapp-ingress
  namespace: microservice
  annotations:
    kubernetes.io/ingress.class: nginx
spec:
  rules:
    - http:
        paths:
          - path: /web/
            pathType: Prefix
            backend:
              service:
                name: webapp
                port:
                  number: 80
```

### File: discovery-ingress.yaml
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: discovery-ingress
  namespace: microservice
  annotations:
    kubernetes.io/ingress.class: nginx
spec:
  ingressClassName: nginx
  rules:
    - http:
        paths:
          - path: /discovery
            pathType: Prefix
            backend:
              service:
                name: discovery
                port:
                  number: 8761
```