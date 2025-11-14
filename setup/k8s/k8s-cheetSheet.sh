
gcloud container clusters delete amit-cluster --zone us-central1-a
kubectl delete namespace kafka --wait


kubectl get ns
kubectl create namespace observability

kubectl get pods -n observability
kubectl get services -n observability
kubectl get deployments -n observability
kubectl get configmaps -n observability
kubectl get pods,services,deployments -n observability
kubectl get all -n observability


kubectl get svc -A | grep LoadBalancer
kubectl get pods -A | grep ingress
kubectl get ingressclass

