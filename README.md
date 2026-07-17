# NOVA 2048

A small, polished **offline Android game** — a neon take on 2048 with sliding-tile
animation, a custom launcher icon, one-level undo and haptic feedback on merges.
Swipe to combine matching numbers and reach 2048. Your best score is saved on the
device. No internet, no permissions, no ads.

**Install it:** grab [`dist/Nova2048.apk`](dist/Nova2048.apk), copy it to an
Android phone (Android 7.0 / API 24 or newer) and open it. You'll need to allow
"install from unknown sources" for your browser or file manager the first time.

```
Package : com.nova.blocks
Label   : Nova 2048
Min SDK : 24 (Android 7.0)   Target SDK : 34
Signing : APK Signature Scheme v2
Size    : ~22 KB
```

## What's interesting about this build

There is no Android SDK in the environment this was built in (Google's
`dl.google.com`, where `aapt2` / `d8` / `apksigner` live, is unreachable), so the
APK is assembled **by hand** from parts available on Maven Central plus the JDK:

| Job                         | Normally (SDK) | Here                                         |
| --------------------------- | -------------- | -------------------------------------------- |
| Compile classpath           | `android.jar`  | `org.robolectric:android-all`                |
| `.class` -> `.dex`          | `d8`           | `com.jakewharton...:dalvik-dx`               |
| Compile `AndroidManifest`   | `aapt2`        | `tools/make_manifest.py` (hand-written AXML) |
| Compile `resources.arsc`    | `aapt2`        | `tools/make_arsc.py` (hand-written table)    |
| Align (`resources.arsc`)    | `zipalign`     | `tools/apkzip.py` (4-byte aligned zip)       |
| Sign (v2)                   | `apksigner`    | `com.android.tools.build:apksig` + `keytool` |

Two encoders do the heavy lifting. Android doesn't read the textual manifest, it
reads a compiled **binary-XML** resource, and it resolves `@mipmap/ic_launcher`
through a compiled **resource table** (`resources.arsc`). Both formats are emitted
chunk-by-chunk (string pools, resource map, typed values), with the framework
attribute/resource IDs read straight out of `android.jar` (`tools/DumpIds.java`).
`resources.arsc` must be stored uncompressed and 4-byte aligned for apps targeting
SDK 30+, so `apkzip.py` builds the zip with zipalign-style padding that apksig then
preserves while signing.

Everything was cross-checked: the manifest and resource table were re-parsed with
an independent tool (androguard) — the icon id `0x7f010000` resolves back to
`res/mipmap/ic_launcher.png` — and the signature was checked with apksig's verifier.

## Build it yourself

Requires a JDK (21 is fine), Python 3, and network access to Maven Central. The
first run downloads ~140 MB of tooling into `.buildcache/` (git-ignored).

```bash
./build.sh
# -> dist/Nova2048.apk
```

## Layout

```
app/java/com/nova/blocks/   MainActivity.java, GameView.java   (the game)
app/res/mipmap/             ic_launcher.png                    (the icon asset)
tools/DumpIds.java          dumps framework attr/resource IDs from android.jar
tools/make_manifest.py      binary AndroidManifest.xml encoder (no aapt2)
tools/make_arsc.py          binary resources.arsc encoder (no aapt2)
tools/make_icon.py          renders the launcher icon (Pillow)
tools/apkzip.py             zipalign-style aligned APK zip builder
tools/Signer.java           v2 signing via apksig
tools/Verifier.java         signature verification via apksig
build.sh                    compile -> dex -> manifest -> arsc -> zip -> sign
dist/Nova2048.apk           the built, signed, installable app
```

## Gameplay

- **Swipe** up / down / left / right — tiles slide with animation.
- Two tiles with the same number **merge** (with a little haptic buzz) into their
  sum and add to your score.
- Reach **2048** to win — tap to keep going; the board fills up = game over.
- **UNDO** reverts the last move; **NEW GAME** restarts.

The whole UI is drawn in code with `Canvas`; the only bundled resource is the
launcher icon, wired up through the hand-built `resources.arsc`.
