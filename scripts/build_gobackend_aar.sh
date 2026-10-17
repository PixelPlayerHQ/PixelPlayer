#!/usr/bin/env bash
# Rebuilds app/libs/gobackend.aar from SpotiFLAC-Mobile go_backend.
# Usage: SPOTIFLAC_DIR=/path/to/SpotiFLAC-Mobile ./scripts/build_gobackend_aar.sh
set -euo pipefail
: "${SPOTIFLAC_DIR:?Set SPOTIFLAC_DIR}"
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME}"
export CGO_ENABLED=1
cd "$SPOTIFLAC_DIR/go_backend"
echo "Pinning SpotiFLAC commit: $(git rev-parse HEAD)"
go mod download
command -v gomobile >/dev/null || go install golang.org/x/mobile/cmd/gomobile
gomobile init
mkdir -p "$(dirname "$0")/../app/libs"
gomobile bind -target=android/arm64 -androidapi 24 -o "$(dirname "$0")/../app/libs/gobackend.aar" .
echo "OK"
