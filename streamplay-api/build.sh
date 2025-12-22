#!/bin/bash
set -e

DOCKER_USER="vanillaboard"
IMAGE_NAME="streamplay-api"
PLATFORM="linux/arm64"
TAG="${1:-latest}"

FULL_IMAGE="$DOCKER_USER/$IMAGE_NAME:$TAG"

echo "Building $FULL_IMAGE for $PLATFORM..."

# Build und ins lokale Docker laden
docker buildx build \
    --platform "$PLATFORM" \
    -t "$FULL_IMAGE" \
    --load \
    .

echo "Pushing $FULL_IMAGE..."
docker push "$FULL_IMAGE"

echo "Done! Pushed: $FULL_IMAGE"
