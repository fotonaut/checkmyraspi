#!/usr/bin/env bash
# Baut ein signiertes Release-APK und aktualisiert die Downloadseite (website/).
# Aufruf: scripts/release.sh            (Version aus app/build.gradle.kts)
# Voraussetzung: keystore.properties im Projektwurzelverzeichnis.
set -euo pipefail
cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-/home/andi/dev-tools/jdk-21}"
SDK="${ANDROID_SDK_ROOT:-/home/andi/dev-tools/android-sdk}"
APKSIGNER="$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)apksigner"

VERSION_NAME=$(sed -n 's/^val appVersionName = "\(.*\)"/\1/p' app/build.gradle.kts)
VERSION_CODE=$(sed -n 's/^val appVersionCode = \([0-9]*\)/\1/p' app/build.gradle.kts)
[[ -n "$VERSION_NAME" && -n "$VERSION_CODE" ]] || { echo "Version nicht gefunden" >&2; exit 1; }
[[ -f keystore.properties ]] || { echo "keystore.properties fehlt – Release wäre unsigniert" >&2; exit 1; }

echo "==> CheckMyRaspi $VERSION_NAME ($VERSION_CODE)"
./gradlew --quiet :app:testDebugUnitTest :app:lintDebug :app:assembleRelease

SRC=app/build/outputs/apk/release/app-release.apk
NAME="checkmyraspi-$VERSION_NAME.apk"
"$APKSIGNER" verify "$SRC"

mkdir -p dist website/downloads
cp "$SRC" "dist/$NAME"
cp "$SRC" "website/downloads/$NAME"
SHA=$(sha256sum "dist/$NAME" | cut -d' ' -f1)
SIZE=$(stat -c %s "dist/$NAME")
DATE_ISO=$(date +%F)
echo "$SHA  $NAME" > "website/downloads/$NAME.sha256"

cat > website/version.json <<JSON
{
  "versionName": "$VERSION_NAME",
  "versionCode": $VERSION_CODE,
  "date": "$DATE_ISO",
  "minSdk": 26,
  "apk": "downloads/$NAME",
  "sizeBytes": $SIZE,
  "sha256": "$SHA"
}
JSON

python3 - "$VERSION_NAME" "$NAME" "$SHA" "$SIZE" "$DATE_ISO" <<'PY'
import re, sys, datetime
ver, name, sha, size, date = sys.argv[1:]
months = ["Januar","Februar","März","April","Mai","Juni","Juli","August","September","Oktober","November","Dezember"]
d = datetime.date.fromisoformat(date)
human = f"{d.day}. {months[d.month-1]} {d.year}"
mb = f"{int(size)/1_000_000:.1f}".replace(".", ",")
block = f"""<!-- release:begin -->
      <div class="cta">
        <a class="btn primary" href="downloads/{name}" download>⬇ APK herunterladen</a>
        <a class="btn secondary" href="#installation">Installation</a>
      </div>
      <p class="meta">Version {ver} · {human} · {mb} MB · Android 8 oder neuer</p>
      <p class="meta">SHA-256: <code class="hash">{sha}</code></p>
      <!-- release:end -->"""
p = "website/index.html"
s = open(p, encoding="utf-8").read()
s, n = re.subn(r"<!-- release:begin -->.*?<!-- release:end -->", block, s, flags=re.S)
assert n == 1, "release-Marker nicht gefunden"
open(p, "w", encoding="utf-8").write(s)
PY

echo "==> dist/$NAME"
echo "    SHA-256 $SHA"
echo "    Changelog in CHANGELOG.md und website/index.html (#changelog) pflegen, dann scripts/deploy-website.sh"
