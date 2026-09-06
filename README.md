# Aiko Lingo

An Android companion app for learning English with Aiko.

This is intentionally separate from the Aiko-chan web UI. The app will connect to the Aiko server over Tailscale and provide translation and guided conversation modes.

## Current status

Initial project design only. The first implementation target is a native Kotlin/Jetpack Compose app with:

- Translation mode with multiple Japanese registers
- Beginner, intermediate, and advanced conversation modes
- Typewriter/karaoke dialogue presentation
- Hint and Stop controls
- Light shoujo and dark purple glass themes

## Development

Open this folder in Android Studio, install the Android SDK, and run it on an emulator or phone. The server URL should eventually be the Tailscale address of Aiko-chan, for example `http://100.x.x.x:8787`.

## Planned server contract

```text
POST /api/english/translate
POST /api/english/conversation/start
POST /api/english/conversation/respond
POST /api/english/conversation/hint
POST /api/english/conversation/stop
```
