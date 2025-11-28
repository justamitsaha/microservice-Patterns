# Kubernetes Ingress Routing Issue

## 1. ROLE & TECH STACK
**Role:** Act as a Senior Expert in Spring, Spring Boot, Java web development, and Kubernetes/Nginx ingress.

**Current Stack:**
- **Language/Version:** Java 21
- **Framework/Library:** Spring Boot 3.5/3.6, Angular
- **Infrastructure:** GCP, Docker, Kubernetes with Nginx Ingress Controller

## 2. CONTEXT & OBJECTIVE

**The Goal:** Set up ingress routing for Angular web app at `/web` and Spring Boot Eureka server at `/discovery`

**The Problem:** 
- Web App showing nginx 404 at `http://35.226.236.202/web`
- Eureka Dashboard showing 404 at `http://35.226.236.202/discovery`
- Eureka Actuator showing 404 at `http://35.226.236.202/discovery/actuator/health`
- Static files getting 404: `http://35.226.236.202/styles-43BJAFET.css`

**Specific Questions:** 
1. I already have `<base href="/web/">` in my index.html - do I need to make changes in the Dockerfile or nginx.conf as well?
2. How is the web-app configuration causing issues for the discovery ingress routing?
3. Why are static assets not loading correctly (returning 404)?

## 3. CODE ARTIFACTS

Please review the following code/configuration files.

### File: index.html
```html
<!doctype html>
<html lang="en">

<head>
  <meta charset="utf-8">
  <title>Webapp</title>
  <base href="/web/">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <link rel="icon" type="image/x-icon" href="favicon.ico">
  <script src="assets/env.js"></script>
</head>

<body>
  <app-root></app-root>
</body>

</html>
```

### File: Dockerfile
```dockerfile
FROM node:20-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm install

COPY . .
RUN npm run build --prod

FROM nginx:1.25-alpine

# Copy browser build
COPY --from=build /app/dist/webapp/browser /usr/share/nginx/html

# Copy custom nginx config
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf

# Copy env template
COPY docker/env.template.js /usr/share/nginx/html/assets/env.template.js

# Copy entrypoint script
COPY docker/docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

EXPOSE 80
ENTRYPOINT ["/docker-entrypoint.sh"]
```

### File: nginx.conf
```nginx
# Please provide the contents of docker/nginx.conf if available
```

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
          - path: /web
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

## 4. ADDITIONAL CONTEXT

**What I've tried:**
- Added `<base href="/web/">` to index.html
- Configured ingress with `/web` path prefix

**What's missing:**
- Contents of `docker/nginx.conf` (needed for diagnosis)
- Discovery service configuration (if relevant to routing)