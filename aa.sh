#!/bin/bash

# Android Auto Desktop Head Unit (DHU) Starter
# Voraussetzung: Android SDK mit DHU installiert

# Standard SDK Pfad
SDK_PATH="${ANDROID_HOME:-$HOME/Android/Sdk}"
DHU_PATH="$SDK_PATH/extras/google/auto/desktop-head-unit"

# Prüfe ob DHU existiert
if [ ! -f "$DHU_PATH" ]; then
    echo "Fehler: Desktop Head Unit nicht gefunden unter: $DHU_PATH"
    echo ""
    echo "Installiere DHU über Android Studio:"
    echo "  SDK Manager -> SDK Tools -> Android Auto Desktop Head Unit Emulator"
    exit 1
fi

# Starte Head Unit Server auf dem Gerät
echo "Starte Head Unit Server auf dem Gerät..."
adb shell am start-foreground-service com.google.android.projection.gearhead/.companion.HeadUnitServerService 2>/dev/null || \
adb shell am startservice com.google.android.projection.gearhead/.companion.HeadUnitServerService 2>/dev/null

# Kurz warten bis Server bereit ist
sleep 2

# Starte ADB Port-Forwarding für Android Auto
echo "Starte ADB Port-Forwarding..."
adb forward tcp:5277 tcp:5277

# Starte DHU
echo "Starte Android Auto Desktop Head Unit..."
"$DHU_PATH" -u
