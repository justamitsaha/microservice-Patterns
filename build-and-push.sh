#!/usr/bin/env bash
set -euo pipefail

# Build and optionally push images.
# Naming: <repo>/new-ms-<service-name>:<version>
# Usage examples:
#   DOCKER_USER=justamitsaha VERSION=v1 bash build-and-push.sh
#   REPO=myorg VERSION=v2 PUSH=y bash build-and-push.sh

need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing dependency: $1"; exit 1; }; }
need docker

# Defaults (can be overridden by env)
REPO_DEFAULT=${REPO:-${DOCKER_USER:-justamitsaha}}
VERSION_DEFAULT=${VERSION:-v1}
PUSH_DEFAULT=${PUSH:-}

# Interactive prompts (skip if CI=1 or NO_PROMPT=1)
if [[ "${CI:-}" != "1" && "${NO_PROMPT:-}" != "1" && -t 0 ]]; then
  read -r -p "Repository/namespace [${REPO_DEFAULT}]: " REPO_INPUT || true
  REPO=${REPO_INPUT:-$REPO_DEFAULT}
  read -r -p "Image version tag [${VERSION_DEFAULT}]: " VERSION_INPUT || true
  VERSION=${VERSION_INPUT:-$VERSION_DEFAULT}
  if [[ -z "${PUSH_DEFAULT}" ]]; then
    read -r -p "Push images to Docker Hub? (y/N): " PUSH_INPUT || true
    PUSH=${PUSH_INPUT:-N}
  else
    PUSH=${PUSH_DEFAULT}
  fi
else
  REPO=${REPO_DEFAULT}
  VERSION=${VERSION_DEFAULT}
  PUSH=${PUSH_DEFAULT:-N}
fi

echo "Repository: ${REPO}"
echo "Version:    ${VERSION}"
echo "Push:       ${PUSH}"

build_service() {
  local dir="$1"; shift
  local name="$1"; shift
  local img="${REPO}/${name}:${VERSION}"

  local do_build="Y"
  if [[ "${CI:-}" != "1" && "${NO_PROMPT:-}" != "1" && -t 0 ]]; then
    read -r -p "Build ${img}? (Y/n): " ans || true
    do_build=${ans:-Y}
  fi
  if [[ ! "$do_build" =~ ^[Yy]$ ]]; then
    echo "Skipping ${img}"
    return 0
  fi

  echo "\n=== Building ${img} from ./${dir} ==="
  docker build -t "${img}" "./${dir}"

  if [[ "$PUSH" =~ ^[Yy]$ ]]; then
    echo "\n=== Pushing ${img} ==="
    docker push "${img}"
  else
    echo "Skipping push for ${img} (PUSH=${PUSH})"
  fi
}

# Services to build
build_service configService        new-ms-config-service
build_service discoveryService     new-ms-discovery-service
build_service gatewayService       new-ms-gateway-service
build_service customerService      new-ms-customer-service
build_service reactiveOrderService new-ms-reactive-order-service
build_service webapp               new-ms-webapp

echo "\nDone. Built images under ${REPO} with tag ${VERSION}."
