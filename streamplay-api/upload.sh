#!/bin/bash

# Konfiguration
API_URL="http://localhost:3000"
ADMIN_USER="admin123"
ADMIN_PASS="password123"
USER_NAME="user123"
USER_PASS="password123"
JSON_FILE="test.json"

# Prüfen ob JSON-Datei existiert
if [ ! -f "$JSON_FILE" ]; then
    echo "Fehler: $JSON_FILE nicht gefunden!"
    exit 1
fi

echo "1. Admin Login..."
ADMIN_TOKEN=$(curl -s -X POST "$API_URL/api/admin/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" | jq -r '.token')

if [ "$ADMIN_TOKEN" == "null" ] || [ -z "$ADMIN_TOKEN" ]; then
    echo "Fehler: Admin Login fehlgeschlagen!"
    exit 1
fi
echo "   OK"

echo "2. User '$USER_NAME' erstellen..."
curl -s -X POST "$API_URL/api/admin/users" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"username\":\"$USER_NAME\",\"password\":\"$USER_PASS\"}" > /dev/null
echo "   OK (oder existiert bereits)"

echo "3. User Login..."
USER_TOKEN=$(curl -s -X POST "$API_URL/api/user/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USER_NAME\",\"password\":\"$USER_PASS\"}" | jq -r '.token')

if [ "$USER_TOKEN" == "null" ] || [ -z "$USER_TOKEN" ]; then
    echo "Fehler: User Login fehlgeschlagen!"
    exit 1
fi
echo "   OK"

echo "4. JSON hochladen..."
RESPONSE=$(curl -s -X PUT "$API_URL/api/user/data" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -d "{\"data\": $(cat "$JSON_FILE")}")

echo "   Antwort: $RESPONSE"
echo ""
echo "Fertig!"
