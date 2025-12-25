#!/bin/bash

# ---------------------------------------------
# GKE Automated Setup Script
# Author: Amit Saha
# ---------------------------------------------

PROJECT_ID=$(gcloud config get-value project)
REGION="us-central1"
ZONE="us-central1-a"
CLUSTER_NAME="amit-cluster"
CLUSTER_SIZE="large"  # default small unless provided

# -------- Read cluster size argument ----------
if [ ! -z "$1" ]; then
  CLUSTER_SIZE=$1
fi

echo "🧠 Project: $PROJECT_ID"
echo "📍 Zone: $ZONE | Region: $REGION"
echo "🚀 Cluster Size: $CLUSTER_SIZE"
echo "----------------------------------------"

# -------- 1. Enable Required Services --------
echo "🔧 Enabling Kubernetes Engine API..."
gcloud services enable container.googleapis.com

# -------- 2. Set default region/zone ----------
echo "🌍 Setting default zone and region..."
gcloud config set compute/zone $ZONE
gcloud config set compute/region $REGION

# -------- 3. Create GKE Cluster --------------
echo "🏗️ Creating GKE Cluster..."

if [ "$CLUSTER_SIZE" == "large" ]; then
  echo "📦 Creating LARGE cluster..."
  gcloud container clusters create $CLUSTER_NAME \
    --zone $ZONE \
    --num-nodes 3 \
    --machine-type e2-standard-2 \
    --enable-ip-alias \
    --no-enable-autoscaling \
    --tags=gke-node
else
  echo "📦 Creating SMALL cluster..."
  gcloud container clusters create $CLUSTER_NAME \
    --zone $ZONE \
    --num-nodes 2 \
    --machine-type e2-small \
    --enable-ip-alias \
    --no-enable-autoscaling \
    --tags=gke-node
fi

# -------- 4. Enable HTTP Load Balancer Add-on ----
echo "⚙️  Enabling HTTP Load Balancing Addon..."
gcloud container clusters update $CLUSTER_NAME \
  --zone $ZONE \
  --update-addons=HttpLoadBalancing=ENABLED

# -------- 5. Connect kubectl -------------------
echo "🔗 Connecting kubectl to cluster..."
gcloud container clusters get-credentials $CLUSTER_NAME --zone $ZONE

echo "✅ Verifying connection..."
kubectl get nodes
kubectl get pods -A

# -------- 6. Install NGINX Ingress Controller ----
echo "📦 Installing NGINX Ingress Controller..."
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update

helm install nginx-ingress ingress-nginx/ingress-nginx \
  --namespace ingress-nginx \
  --create-namespace \
  --set controller.publishService.enabled=true

# -------- 7. Final Verification -----------------
echo "🔍 Checking Ingress Controller Status..."
kubectl get pods -n ingress-nginx

echo ""
echo "🎉 Setup Completed Successfully!"
echo "Run this to get LB IP:"
echo "kubectl get svc -n ingress-nginx"
