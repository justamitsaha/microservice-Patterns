kubectl apply -f configmap-webapp-env.yaml
kubectl apply -f web-app-deployment-webapp.yaml
kubectl apply -f wen-app-service-webapp.yaml
kubectl apply -f ingress-shared.yaml


kubectl get ingress

