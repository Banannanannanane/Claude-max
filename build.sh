#!/usr/bin/env bash
# ============================================================
# Construit dist/MiniJeux.apk sans le SDK Android officiel.
# Outils requis : javac, dalvik-exchange (dx), aapt, zipalign,
# apksigner, python3 + Pillow, et un android.jar de compilation.
#
#   ANDROID_JAR=/chemin/android.jar ./build.sh
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR="${ANDROID_JAR:-tools/android.jar}"
if [ ! -f "$ANDROID_JAR" ]; then
  echo "android.jar introuvable — téléchargement depuis Maven Central..."
  mkdir -p tools
  curl -sSL -o "$ANDROID_JAR" \
    "https://repo1.maven.org/maven2/com/google/android/android/4.1.1.4/android-4.1.1.4.jar"
fi

BUILD=build
rm -rf "$BUILD"
mkdir -p "$BUILD/classes" dist

echo "==> Icônes"
python3 tools/make_icon.py app/res

echo "==> Compilation Java"
javac --release 8 -nowarn -cp "$ANDROID_JAR" -d "$BUILD/classes" \
  $(find app/src -name '*.java')

echo "==> DEX"
dalvik-exchange --dex --min-sdk-version=21 \
  --output="$BUILD/classes.dex" "$BUILD/classes"

echo "==> Empaquetage des ressources"
aapt package -f \
  -M app/AndroidManifest.xml \
  -S app/res \
  -A app/assets \
  -I "$ANDROID_JAR" \
  -F "$BUILD/app.unsigned.apk"

(cd "$BUILD" && aapt add app.unsigned.apk classes.dex >/dev/null)

echo "==> Alignement"
zipalign -f 4 "$BUILD/app.unsigned.apk" "$BUILD/app.aligned.apk"

echo "==> Signature"
KEYSTORE=release.keystore
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -v \
    -keystore "$KEYSTORE" -storepass minijeux -keypass minijeux \
    -alias minijeux -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Mini Jeux" >/dev/null 2>&1
fi
apksigner sign \
  --ks "$KEYSTORE" --ks-pass pass:minijeux --key-pass pass:minijeux \
  --out dist/MiniJeux.apk "$BUILD/app.aligned.apk"

apksigner verify --print-certs dist/MiniJeux.apk | head -3
echo
echo "OK -> dist/MiniJeux.apk ($(du -h dist/MiniJeux.apk | cut -f1))"
