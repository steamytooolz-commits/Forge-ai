#!/usr/bin/env bash
# ==============================================================================
# Generate Android Release Keystore (JKS) & Output Base64 for CI Secrets
# ==============================================================================
set -euo pipefail

OUTPUT_JKS="${1:-my-upload-key.jks}"
ALIAS="${2:-upload}"
STORE_PASS="${3:-${STORE_PASSWORD:-}}"
KEY_PASS="${4:-${KEY_PASSWORD:-${STORE_PASS}}}"
DNAME="${5:-CN=Forge Release,OU=Mobile,O=Forge,C=US}"

if [[ -z "$STORE_PASS" ]]; then
    read -rsp "Enter Keystore Password: " STORE_PASS
    echo
    read -rsp "Confirm Keystore Password: " STORE_PASS_CONFIRM
    echo
    if [[ "$STORE_PASS" != "$STORE_PASS_CONFIRM" ]]; then
        echo "❌ Passwords do not match."
        exit 1
    fi
    KEY_PASS="$STORE_PASS"
fi

echo "==> Generating Release Keystore: $OUTPUT_JKS (Alias: $ALIAS)..."

keytool -genkeypair -v \
    -keystore "$OUTPUT_JKS" \
    -storepass "$STORE_PASS" \
    -alias "$ALIAS" \
    -keypass "$KEY_PASS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "$DNAME"

echo "✔ Release keystore created at $OUTPUT_JKS"
echo "==> Base64 string for GitHub Actions Secret (KEYSTORE_BASE64):"
echo "--------------------------------------------------------------------------------"
base64 -w 0 "$OUTPUT_JKS" 2>/dev/null || base64 "$OUTPUT_JKS"
echo
echo "--------------------------------------------------------------------------------"
echo "Store the above output in GitHub Repository Secrets as KEYSTORE_BASE64."
echo "Also configure STORE_PASSWORD and KEY_PASSWORD in repository secrets."
