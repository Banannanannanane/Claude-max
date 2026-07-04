#!/usr/bin/env bash
# Construit UsineStellaire.apk sans Gradle ni SDK Android complet.
# Prérequis : openjdk (javac, keytool), aapt, zipalign, apksigner (paquets Debian/Ubuntu),
#             r8lib.jar (D8, compilateur dex) et android-all-14.jar (classpath Android, Maven Central).
# Usage : TOOLS=/chemin/vers/jars ./build.sh
set -euo pipefail
cd "$(dirname "$0")"

TOOLS="${TOOLS:-.}"
R8="$TOOLS/r8lib.jar"
ANDROID_JAR="$TOOLS/android-all-14.jar"
FRAMEWORK_RES="/usr/share/android-framework-res/framework-res.apk"
KS=usine.keystore
KS_PASS=usine-stellaire-2026

rm -rf build && mkdir -p build/classes build/dex

echo "— 1/6 : jeu → assets"
cp ../index.html assets/index.html
cp ../three.bundle.js assets/three.bundle.js

echo "— 2/6 : javac"
javac --release 8 -Xlint:-options -cp "$ANDROID_JAR" \
      -d build/classes src/com/usinestellaire/app/MainActivity.java

echo "— 3/6 : D8 (dex)"
java -cp "$R8" com.android.tools.r8.D8 --release --min-api 23 \
     --lib "$ANDROID_JAR" --output build/dex \
     build/classes/com/usinestellaire/app/*.class

echo "— 4/6 : aapt (ressources + manifest + assets)"
aapt package -f -M AndroidManifest.xml -S res -A assets \
     -I "$FRAMEWORK_RES" -F build/base.apk
cp build/dex/classes.dex build/
(cd build && aapt add base.apk classes.dex >/dev/null)

echo "— 5/6 : zipalign"
zipalign -f 4 build/base.apk build/aligned.apk

echo "— 6/6 : signature (APK Signature Scheme v1+v2)"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias usine -keyalg RSA -keysize 2048 \
          -validity 10000 -storepass "$KS_PASS" -keypass "$KS_PASS" \
          -dname "CN=Usine Stellaire, O=Perso"
fi
apksigner sign --ks "$KS" --ks-key-alias usine \
          --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
          --out ../UsineStellaire.apk build/aligned.apk
apksigner verify --verbose ../UsineStellaire.apk | head -5
echo "OK → $(cd .. && pwd)/UsineStellaire.apk"
