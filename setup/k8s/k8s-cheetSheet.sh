
gcloud container clusters delete amit-cluster --zone us-central1-a
bash .\setup\k8s\setup-gke.sh 
bash .\setup\k8s\kafka\install-kafka.sh
bash .\setup\k8s\observability\install.sh 
kubectl delete namespace <> --wait


kubectl get ns
kubectl create namespace <>

kubectl get pods -n <>
kubectl get services -n <>
kubectl get deployments -n <>
kubectl get configmaps -n <>
kubectl get pods,services,deployments -n <>
kubectl get all -n <>


kubectl logs deployment/configservice -n microservice

#When pod not started like ContainerCreating  status
kubectl describe pod configservice-567d66cdfd-jc5t6 -n microservice
kubectl get pod configservice-567d66cdfd-jc5t6 -n microservice -o wide

#if pod startarting ten getting error like CrashLoopBackOff
kubectl logs deployment/configservice -n microservice



#check config map
kubectl get configmap -n microservice


kubectl get svc -A | grep LoadBalancer
kubectl get pods -A | grep ingress
kubectl get svc -n ingress-nginx
kubectl get ingressclass

