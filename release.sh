#!/usr/bin/env bash
# Builds Flip Music, writes the update manifest and publishes everything to player.pjsk.store.
#
#   ./release.sh "更新内容第一行
#   更新内容第二行"
#
# Steps: assembleRelease → dist/FlipMusic-<version>.apk → site/update.json (version, size, SHA-256,
# notes) → rsync site/ and the APK to the server → point download/latest.apk at the new build.
# The app reads https://player.pjsk.store/update.json, so this is what ships an update.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
APP="$ROOT/YouFlipMusic"
SITE="$ROOT/site"
DIST="$ROOT/dist"
HOST="${FLIP_HOST:-root@8.209.235.71}"
REMOTE="${FLIP_REMOTE:-/var/www/player.pjsk.store}"
BASE_URL="https://player.pjsk.store"
NOTES="${1:-}"

cd "$APP"
NAME=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)
CODE=$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts)
echo "== Building $NAME ($CODE)"
./gradlew -q assembleRelease

mkdir -p "$DIST"
APK="FlipMusic-$NAME.apk"
cp app/build/outputs/apk/release/app-release.apk "$DIST/$APK"
SIZE=$(stat -f%z "$DIST/$APK" 2>/dev/null || stat -c%s "$DIST/$APK")
SHA=$(shasum -a 256 "$DIST/$APK" | cut -d' ' -f1)

python3 - "$SITE/update.json" "$CODE" "$NAME" "$BASE_URL/download/$APK" "$NOTES" "$SIZE" "$SHA" <<'EOF'
import json, sys
path, code, name, url, notes, size, sha = sys.argv[1:]
json.dump({"versionCode": int(code), "versionName": name, "url": url, "notes": notes,
           "size": int(size), "sha256": sha}, open(path, "w"), ensure_ascii=False, indent=2)
EOF
echo "== Manifest: $(tr -d '\n' < "$SITE/update.json" | cut -c1-160)…"

echo "== Uploading to $HOST:$REMOTE"
ssh "$HOST" "mkdir -p '$REMOTE/download'"
rsync -az "$DIST/$APK" "$HOST:$REMOTE/download/$APK"
# The page and icon first, the manifest last, so the app never sees a version whose APK isn't there yet.
rsync -az --exclude update.json --exclude deploy "$SITE/" "$HOST:$REMOTE/"
ssh "$HOST" "ln -sfn '$APK' '$REMOTE/download/latest.apk'"
rsync -az "$SITE/update.json" "$HOST:$REMOTE/update.json"
echo "== Published $NAME → $BASE_URL"
