#!/bin/bash
set -e

IMAGE_NAME="vanillaboard/streamplay-api"
PLATFORMS="linux/arm64"

echo "=== Streamplay API Docker Build ==="

# Prüfen ob eingeloggt
if ! docker info 2>/dev/null | grep -q "Username"; then
    echo "Nicht bei Docker Hub eingeloggt. Bitte einloggen:"
    docker login
fi

# Buildx installieren falls nötig
if ! docker buildx version &>/dev/null; then
    echo "Installiere docker buildx..."

    # Buildx binary herunterladen
    BUILDX_VERSION="v0.19.3"
    mkdir -p ~/.docker/cli-plugins
    curl -sSL "https://github.com/docker/buildx/releases/download/${BUILDX_VERSION}/buildx-${BUILDX_VERSION}.linux-amd64" -o ~/.docker/cli-plugins/docker-buildx
    chmod +x ~/.docker/cli-plugins/docker-buildx

    echo "Buildx installiert: $(docker buildx version)"
fi

# Builder erstellen falls nicht vorhanden
if ! docker buildx inspect multiarch-builder &>/dev/null 2>&1; then
    echo "Erstelle Multi-Arch Builder..."
    docker buildx create --name multiarch-builder --use
    docker buildx inspect --bootstrap
else
    docker buildx use multiarch-builder
fi

# QEMU für Cross-Compile (arm64 auf x86)
echo "Aktiviere QEMU für Cross-Platform Build..."
docker run --rm --privileged multiarch/qemu-user-static --reset -p yes 2>/dev/null || true

# Bauen und pushen
echo "Baue Image für $PLATFORMS..."
docker buildx build \
    --platform "$PLATFORMS" \
    -t "$IMAGE_NAME:latest" \
    -t "$IMAGE_NAME:arm64" \
    --push .

echo ""
echo "=== Fertig! ==="
echo "Image gepusht: $IMAGE_NAME:latest"
echo "Auf ARM-Server pullen mit: docker pull $IMAGE_NAME:latest"
