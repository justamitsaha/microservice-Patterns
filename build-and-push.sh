#!/usr/bin/env bash
set -euo pipefail

# Build and push all service images to Docker Hub (or any registry)
# Naming convention: <repo>/new-ms-<service-name>:<version>
# Usage examples:
#   DOCKER_USER=justamitsaha VERSION=v1 bash build-and-push.sh
#   REPO=myorg VERSION=v2 bash build-and-push.sh

REPO=${REPO:-${DOCKER_USER:-justamitsaha}}
VERSION=${VERSION:-v1}

echo "Repository: ${REPO}"
echo "Version:    ${VERSION}"

need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing dependency: $1"; exit 1; }; }
need docker

build_push() {
  local dir="$1"; shift
  local name="$1"; shift
  local img="${REPO}/${name}:${VERSION}"
  echo "\n=== Building ${img} from ./${dir} ==="
  docker build -t "${img}" "./${dir}"
  echo "\n=== Pushing ${img} ==="
  docker push "${img}"
}

# Java Spring Boot services
build_push configService        new-ms-config-service
build_push discoveryService     new-ms-discovery-service
build_push gatewayService       new-ms-gateway-service
build_push customerService      new-ms-customer-service
build_push reactiveOrderService new-ms-reactive-order-service

# Web app
build_push webapp               new-ms-webapp

echo "\nAll images built and pushed to ${REPO} with tag ${VERSION}."

