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

# Prüfe ob Gerät verbunden ist
if ! adb devices | grep -qE "device\+?$"; then
    echo "Fehler: Kein Android-Gerät verbunden oder nicht autorisiert"
    echo "  Prüfe USB-Verbindung und USB-Debugging"
    exit 1
fi

# Entferne eventuell vorhandenes Port-Forwarding (verhindert Konflikte)
adb forward --remove tcp:5277 2>/dev/null

# Force-Stop Android Auto um leaked connections zu vermeiden
echo "Stoppe Android Auto auf dem Gerät..."
adb shell am force-stop com.google.android.projection.gearhead
sleep 1

# Starte DHU im USB-Modus (stabiler als TCP)
echo "Starte Android Auto Desktop Head Unit (USB-Modus)..."
"$DHU_PATH" -u
