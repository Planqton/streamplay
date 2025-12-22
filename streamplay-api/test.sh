#!/bin/bash

# Test-Umgebungsvariablen
export ADMIN_USERNAME=admin123
export ADMIN_PASSWORD=password123
export JWT_SECRET=secret123

# Docker Image bauen und Container starten
docker build -f Dockerfile.test -t streamplay-api-test . && \
docker run --rm -p 3000:3000 \
  -e ADMIN_USERNAME=$ADMIN_USERNAME \
  -e ADMIN_PASSWORD=$ADMIN_PASSWORD \
  -e JWT_SECRET=$JWT_SECRET \
  -v "$(pwd)/data:/app/data" \
  streamplay-api-test
