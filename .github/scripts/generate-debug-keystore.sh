#!/usr/bin/env bash
# ==============================================================================
# Generate or Restore Android Debug Keystore
# ==============================================================================
set -euo pipefail

KEYSTORE_FILE="debug.keystore"
KEYSTORE_BASE64="debug.keystore.base64"
STORE_PASS="android"
KEY_PASS="android"
ALIAS="androiddebugkey"
DNAME="CN=Android Debug,O=Android,C=US"
VALIDITY=10000
KEYALG="RSA"
KEYSIZE=2048

echo "==> Checking Android Debug Keystore status..."

if [[ -f "$KEYSTORE_FILE" ]]; then
    echo "✔ Found existing $KEYSTORE_FILE ($(wc -c < "$KEYSTORE_FILE" | tr -d ' ') bytes)."
    exit 0
fi

if [[ -f "$KEYSTORE_BASE64" ]]; then
    echo "==> Restoring $KEYSTORE_FILE from $KEYSTORE_BASE64..."
    if command -v base64 >/dev/null 2>&1; then
        # Try both standard base64 decode flags across Linux and macOS
        if base64 --decode "$KEYSTORE_BASE64" > "$KEYSTORE_FILE" 2>/dev/null; then
            echo "✔ Successfully decoded $KEYSTORE_FILE from base64."
            exit 0
        elif base64 -d "$KEYSTORE_BASE64" > "$KEYSTORE_FILE" 2>/dev/null; then
            echo "✔ Successfully decoded $KEYSTORE_FILE from base64."
            exit 0
        fi
    fi
fi

echo "==> Generating fresh $KEYSTORE_FILE using keytool..."
if ! command -v keytool >/dev/null 2>&1; then
    echo "❌ Error: 'keytool' command not found in PATH. Please ensure JDK/JRE is installed."
    exit 1
fi

keytool -genkey -v \
    -keystore "$KEYSTORE_FILE" \
    -storepass "$STORE_PASS" \
    -alias "$ALIAS" \
    -keypass "$KEY_PASS" \
    -keyalg "$KEYALG" \
    -keysize "$KEYSIZE" \
    -validity "$VALIDITY" \
    -dname "$DNAME"

echo "✔ Successfully generated new $KEYSTORE_FILE."

if [[ "${1:-}" == "--base64" || ! -f "$KEYSTORE_BASE64" ]]; then
    echo "==> Updating $KEYSTORE_BASE64..."
    base64 -w 0 "$KEYSTORE_FILE" 2>/dev/null > "$KEYSTORE_BASE64" || base64 "$KEYSTORE_FILE" > "$KEYSTORE_BASE64"
    echo "✔ Updated $KEYSTORE_BASE64."
fi
