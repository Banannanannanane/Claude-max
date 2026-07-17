#!/usr/bin/env bash
#
# Builds NOVA 2048 into a signed, installable APK *without* the Android SDK.
#
# The usual toolchain (aapt2, d8, apksigner from the SDK build-tools) is not
# available here, so this script assembles the APK from parts fetched off
# Maven Central:
#   - android.jar  : org.robolectric:android-all      (compile classpath)
#   - dx           : com.jakewharton...:dalvik-dx      (.class -> .dex)
#   - apksig       : com.android.tools.build:apksig    (v1+v2 signing)
# plus the JDK's own javac / keytool, and a hand-written binary-manifest
# encoder (tools/make_manifest.py).
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
CACHE="${NOVA_CACHE:-$ROOT/.buildcache}"
LIBS="$CACHE/libs"
WORK="$CACHE/work"
DIST="$ROOT/dist"
PKG="com.nova.blocks"

M2="https://repo1.maven.org/maven2"
ANDROID_ALL="org/robolectric/android-all/14-robolectric-10818077/android-all-14-robolectric-10818077.jar"
DALVIK_DX="com/jakewharton/android/repackaged/dalvik-dx/16.0.1/dalvik-dx-16.0.1.jar"
APKSIG="com/android/tools/build/apksig/2.3.0/apksig-2.3.0.jar"

mkdir -p "$LIBS" "$WORK" "$DIST"

fetch() { # url dest
  if [ ! -s "$2" ]; then echo ">> fetch $(basename "$2")"; curl -sSL --retry 3 --max-time 600 -o "$2" "$1"; fi
}
fetch "$M2/$ANDROID_ALL" "$LIBS/android.jar"
fetch "$M2/$DALVIK_DX"   "$LIBS/dalvik-dx.jar"
fetch "$M2/$APKSIG"      "$LIBS/apksig.jar"

ANDROID_JAR="$LIBS/android.jar"
DX_JAR="$LIBS/dalvik-dx.jar"
APKSIG_JAR="$LIBS/apksig.jar"

rm -rf "$WORK"/* ; mkdir -p "$WORK/classes" "$WORK/tools" "$WORK/apk"

echo ">> extract framework IDs"
javac -d "$WORK/tools" "$ROOT/tools/DumpIds.java" -cp "$ANDROID_JAR"
java -cp "$WORK/tools:$ANDROID_JAR" DumpIds > "$WORK/ids.properties"
cat "$WORK/ids.properties"

echo ">> compile app (Java 8 bytecode for dx)"
find "$ROOT/app/java" -name '*.java' > "$WORK/sources.txt"
javac -source 8 -target 8 -encoding UTF-8 -nowarn -Xlint:-options \
      -classpath "$ANDROID_JAR" -d "$WORK/classes" @"$WORK/sources.txt"

echo ">> dex"
java -cp "$DX_JAR" com.android.dx.command.Main --dex \
      --output="$WORK/apk/classes.dex" "$WORK/classes"

echo ">> encode binary AndroidManifest.xml"
python3 "$ROOT/tools/make_manifest.py" "$WORK/ids.properties" "$WORK/apk/AndroidManifest.xml"

echo ">> zip unsigned apk"
python3 - "$WORK/apk" "$WORK/nova-unsigned.apk" <<'PY'
import sys, zipfile, os
src, out = sys.argv[1], sys.argv[2]
# AndroidManifest.xml first, then classes.dex; both DEFLATE (no alignment needed).
order = ["AndroidManifest.xml", "classes.dex"]
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    for name in order:
        z.write(os.path.join(src, name), name)
print("unsigned apk:", out)
PY

echo ">> keystore"
KS="$CACHE/nova.p12"
if [ ! -s "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storetype PKCS12 \
    -storepass novapass -keypass novapass -alias nova \
    -dname "CN=NOVA 2048, O=Nova Arcade" -keyalg RSA -keysize 2048 -validity 10000
fi

echo ">> sign (APK Signature Scheme v2)"
javac -d "$WORK/tools" -cp "$APKSIG_JAR" "$ROOT/tools/Signer.java"
# apksig 2.3.0 reaches into JDK-internal sun.security.* which the module
# system hides by default on modern JDKs; open them for this run.
java \
  --add-exports java.base/sun.security.x509=ALL-UNNAMED \
  --add-exports java.base/sun.security.pkcs=ALL-UNNAMED \
  --add-exports java.base/sun.security.util=ALL-UNNAMED \
  -cp "$WORK/tools:$APKSIG_JAR" Signer "$KS" novapass \
  "$WORK/nova-unsigned.apk" "$DIST/Nova2048.apk"

echo ">> done: $DIST/Nova2048.apk"
ls -la "$DIST/Nova2048.apk"
