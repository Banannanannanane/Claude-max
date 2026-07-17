# NOVA 2048

A small, polished **offline Android game** — a neon take on 2048. Swipe to slide
the tiles, combine matching numbers, and try to reach 2048. Your best score is
saved on the device. No internet, no permissions, no ads.

**Install it:** grab [`dist/Nova2048.apk`](dist/Nova2048.apk), copy it to an
Android phone (Android 7.0 / API 24 or newer) and open it. You'll need to allow
"install from unknown sources" for your browser or file manager the first time.

```
Package : com.nova.blocks
Label   : Nova 2048
Min SDK : 24 (Android 7.0)   Target SDK : 34
Signing : APK Signature Scheme v2
Size    : ~8 KB
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
| Sign (v2)                   | `apksigner`    | `com.android.tools.build:apksig` + `keytool` |

The trickiest piece is `tools/make_manifest.py`: Android doesn't read the textual
manifest, it reads a compiled **binary-XML** resource. That encoder emits the
chunk format directly (string pool, resource map, typed attributes), reading the
authoritative framework attribute/resource IDs straight out of `android.jar`
(`tools/DumpIds.java`). The result was cross-checked with an independent AXML
parser (androguard) and the signatures with apksig's own verifier.

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
tools/DumpIds.java          dumps framework attr/resource IDs from android.jar
tools/make_manifest.py      binary AndroidManifest.xml encoder (no aapt2)
tools/Signer.java           v2 signing via apksig
tools/Verifier.java         signature verification via apksig
build.sh                    end-to-end: compile -> dex -> manifest -> zip -> sign
dist/Nova2048.apk           the built, signed, installable app
```

## Gameplay

- **Swipe** up / down / left / right to move every tile.
- Two tiles with the same number **merge** into their sum and add to your score.
- Reach **2048** to win (you can keep going); the board fills up = game over.
- Tap **NEW GAME** (or tap the board after a win/loss) to restart.

The whole UI is drawn in code with `Canvas` — the app ships no XML layouts or
resource files, which is what keeps the hand-packaging simple.
