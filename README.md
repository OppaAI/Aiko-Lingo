# Aiko-Lingo

**An Android companion app for learning Japanese with Aiko—locally deployed, privacy-first.**

> Aiko-Lingo is a native Kotlin/Jetpack Compose application that connects to your local Aiko-chan AI server over Tailscale, providing interactive Japanese language learning through guided conversation, real-time translation, spaced-repetition vocabulary review, and adaptive difficulty modes. The app exists as a dedicated frontend precisely because language learning requires a distinct interaction model from general-purpose chat.

**Author:** [OppaAI](https://github.com/OppaAI) · Beautiful British Columbia, Canada

[![Repo](https://img.shields.io/badge/Repo-OppaAI%2FAiko--Lingo-967BB6?logo=github&logoColor=white)](https://github.com/OppaAI/Aiko-Lingo)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
![Status](https://img.shields.io/badge/Status-experimental-orange.svg)

**Frontend:**
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-1.6-4285F4?logo=android&logoColor=white)
![Android](https://img.shields.io/badge/Android-SDK%2034+-3DDC84?logo=android&logoColor=white)

**Backend:**
![LLM](https://img.shields.io/badge/Runtime-llama.cpp-967BB6?logo=ai&logoColor=white)
![Python](https://img.shields.io/badge/Python-3.12-blue?logo=python&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-async-009688?logo=fastapi&logoColor=white)
![SQLite](https://img.shields.io/badge/SQLite-SM--2%20SRS-003B57?logo=sqlite&logoColor=white)
![Ubuntu](https://img.shields.io/badge/Ubuntu-24.04_LTS-orange?logo=ubuntu&logoColor=white)
![CUDA](https://img.shields.io/badge/CUDA-12.6-76B900?logo=nvidia)

---

## Demo

> **Click the thumbnail below to watch on YouTube** ▶

[![Aiko-Lingo Demo](https://img.youtube.com/vi/xRtCmtZQgwI/maxresdefault.jpg)](https://www.youtube.com/watch?v=xRtCmtZQgwI)

---

## Features

### Current (Kotlin/Jetpack Compose + FastAPI backend)

- **Translation Mode** – Translate English → Japanese with support for:
  - 3–5 registers per request (formal 敬語, casual カジュアル, and points in between), generated in one LLM call
  - Per-line audio playback via TTS
- **Conversation Mode** – Guided dialogue with three difficulty levels (beginner / intermediate / advanced):
  - Streaming replies with a typewriter-style karaoke presentation
  - Live mistake detection: incorrect turns surface English feedback plus a corrected Japanese suggestion
  - Contextual hints on demand
  - A non-streaming `/conversation/respond` endpoint alongside the streaming one, for callers that prefer a single round trip
- **Spaced Repetition (SRS)** – Vocabulary encountered in conversation is automatically extracted (kanji-cache lookup with an LLM fallback for anything uncommon) and scheduled with a real SM-2 algorithm:
  - Dedicated review session flow (`Again` / `Hard` / `Good` / `Easy` grading)
  - A "Practice These" widget surfaces the cards you're actually struggling with (tracked by review failure rate), not just whatever's next in the queue
  - Vocabulary reviews feed into Aiko's long-term memory as episodic events
- **Progress Tracking** – Dashboard with XP & levels, day-over-day streaks, total/learned/due card counts, and average ease — all persisted to disk so nothing resets on a server restart. Both conversation turns *and* SRS reviews contribute to XP and streak.
- **Leaderboard** – Rank, XP, and streak display (currently single-player; see [Roadmap](#development-roadmap))
- **Toast Notifications** – Server-driven toasts ("Perfect! Let's keep talking.", level-start greetings, "🌟 word = meaning" on an Easy-graded review) surfaced consistently across the Conversation, Translate, and Review screens
- **Resilience** – Retry affordances on every screen (conversation stream, dashboard stats, review session, translation) instead of dead-end error states; TTS is rate-limited per user with a real `429` response instead of a silent failure
- **UI Polish**
  - Light "Shoujo" theme (soft pastels, rounded corners)
  - Dark "Lavender Glass" theme (deep purples, high contrast)
  - Responsive mobile layouts

### Planned

- **Voice Input** – Microphone-based speech recognition for spoken practice (TTS *output* is already implemented)
- **Multiplayer Leaderboard** – Real cross-user ranking; today's `/leaderboard` endpoint only ever returns the requesting user
- **Offline Mode** – Cached vocabularies and common phrases for low-connectivity scenarios
- **Session Persistence Across Devices** – Currently all per-user state (streaks, XP, level, SRS cards) lives on the Aiko-chan server keyed by session user ID, which already survives app restarts; a future pass will let it sync across multiple client devices for the same user

---

## Quick Start

### Prerequisites
- Android Studio 2024.1+
- Android SDK 34+ (target API level)
- Kotlin 2.0+
- Jetpack Compose 1.6+

### Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/OppaAI/Aiko-Lingo.git
   cd Aiko-Lingo
   ```

2. **Open in Android Studio:**
   - `File` → `Open` → select the `Aiko-Lingo` folder
   - Let Gradle sync and download dependencies

3. **Configure server connection:**
   - The base URL is set in `MainActivity.kt`'s Retrofit builder:
     ```kotlin
     .baseUrl("https://aiko.ide-chroma.ts.net/")
     ```
   - Point this at your own Aiko-chan device's Tailscale hostname or IP (Tailscale handles TLS termination and auth, so plain `https://<tailscale-hostname>/` is normal here — you don't need your own certificate).

4. **Run:**
   - Select an emulator or physical device
   - Click `Run` (▶) or press `Shift + F10`

---

## Architecture

### Client-Side (This App)
```
MainActivity
├── MainMenu
├── TranslateScreen        (TranslateViewModel)
├── ConversationScreen      (ConversationViewModel)
│   ├── LevelSelection
│   ├── DialogueBubble / KaraokeBubble (typewriter streaming)
│   ├── ResponseInput + Hint button
│   └── Toast overlay (server-driven)
├── DashboardScreen          (DashboardViewModel)
│   ├── XP / Streak / Stats cards
│   ├── "Practice These" weak-vocab widget
│   └── → ReviewScreen
├── ReviewScreen              (ReviewViewModel)
│   ├── SM-2 grading (Again / Hard / Good / Easy)
│   └── Toast overlay (server-driven)
└── LeaderboardScreen          (LeaderboardViewModel)
```

### Server-Side (Aiko-chan, FastAPI router at `/api/english`)
```
interface/webui/lingo/
├── router.py   — HTTP routing, streaming, audio, streaks/XP/level bookkeeping
├── srs.py      — SM-2 scheduler over a SQLite-backed vocab table
├── vocab.py    — Japanese tokenization, kanji lookup (+ LLM fallback), romaji
└── models.py   — Pydantic request/response schemas
```

All endpoints authenticate via the caller's session (Tailscale-authenticated), not a client-supplied user ID — this keeps one user's SRS cards, streak, and XP from ever mixing with another's.

### Server Contract

All endpoints live under `/api/english` and rely on session auth rather than a request-body user/session identifier. Conversation continuity (the "session") is client-side: the app resends the running dialogue history with each turn rather than the server tracking a `session_id`.

#### Translate
```http
POST /api/english/translate
Content-Type: application/json

{
  "text": "Hello, how are you?"
}

Response:
{
  "translations": [
    { "register": "Formal", "text": "こんにちは、お元気ですか？", "audioUrl": null },
    { "register": "Casual", "text": "元気？", "audioUrl": null }
  ]
}
```

#### Conversation — Start (streaming, `text/event-stream`)
```http
POST /api/english/conversation/start
Content-Type: application/json

{ "level": "intermediate" }

Stream (newline-delimited JSON):
{"type": "delta", "text": "いらっしゃ"}
{"type": "delta", "text": "いませ！"}
{"type": "final",
 "isCorrect": true,
 "japanese": "いらっしゃいませ！本日のおすすめは天丼です。",
 "english": "Welcome! Today's special is tempura rice bowl.",
 "isFinished": false,
 "audioUrl": "https://.../lingo_audio/....wav",
 "toast": {"type": "info", "message": "Let's practice Japanese at intermediate level! 🔥"}}
```

#### Conversation — Respond (streaming)
```http
POST /api/english/conversation/respond_stream
Content-Type: application/json

{
  "text": "天丼をください。",
  "history": [
    {"speaker": "aiko", "text": "いらっしゃいませ！本日のおすすめは天丼です。"}
  ]
}

Stream: same delta/final shape as Start, plus "vocabExtracted": <int> and a
"toast" on the final chunk (e.g. "Perfect! Let's keep talking.").
```

A non-streaming counterpart, `POST /api/english/conversation/respond`, returns the equivalent payload as a single JSON response for callers that don't want SSE.

#### Conversation — Hint
```http
POST /api/english/conversation/hint

Response:
{
  "japaneseText": "私は天丼を食べたいです。",
  "englishTranslation": "I would like the tempura rice bowl.",
  "audioUrl": "https://.../lingo_audio/....wav",
  "explanation": "This uses the ~ます form correctly — perfect for this level!"
}
```

#### Conversation — Stop
```http
POST /api/english/conversation/stop

Response: { "success": true }
```

#### Text-to-Speech
```http
GET /api/english/tts?text=こんにちは

Response: { "audioUrl": "https://.../lingo_audio/....wav" }
```
Rate-limited per user (20 requests/minute); returns `429` when the limit is hit and `503` only on genuine synthesis failure.

#### SRS Review — Start
```http
POST /api/english/conversation/review/start

Response:
{
  "cards_due": 12,
  "first_card": {"card_id": 4, "hiragana": "たべる", "meaning": "to eat", "context": "食べたいです。"}
}
```
Returns `400` when nothing is due — the client treats this as "you're caught up," not an error.

#### SRS Review — Respond
```http
POST /api/english/conversation/review/respond
Content-Type: application/json

{ "card_id": 4, "response": "to eat", "grade": 3 }

Response:
{
  "updated_card": {"id": 4, "interval": 6, "ease": 2.6},
  "next_card": {"card_id": 9, "hiragana": "のむ", "meaning": "to drink", "context": ""},
  "cards_remaining": 11,
  "toast": {"type": "info", "message": "🌟 のむ = to drink"}
}
```
`grade` is 0–4 (SM-2 scale: Again / Hard / Good / Easy / Perfect). Both correctly and incorrectly graded reviews contribute to the day's streak and XP.

#### Stats / XP / Weak Vocab / Leaderboard
```http
GET /api/english/stats
GET /api/english/weak-vocab
GET /api/english/xp
POST /api/english/xp/add        { "amount": 10 }
GET /api/english/leaderboard
```

---

## Configuration

### Network
- **Tailscale Integration**: Ensure your Android device is on the same Tailscale network as your Aiko-chan server
- **Server Port**: Default is `8787` (configurable in Aiko-chan settings); the FastAPI router itself is mounted under `/api/english` regardless of port
- **HTTPS / SSL**: Not required on Tailscale; traffic is encrypted by default

### Themes
Toggle between themes via the "Switch Theme ✨" button on the main menu:
- **Light (Shoujo)**: Soft pinks, whites, rounded components
- **Dark (Lavender Glass)**: Deep purples, semi-transparent cards, high contrast

### Server-Side Persistence
Per-user state is written to disk under the Aiko-chan working directory so it survives restarts:
```
data/streaks/{user_id}.json     # current streak, total sessions, last practiced date
data/levels/{user_id}.json      # last-used difficulty level
data/xp/{user_id}.json          # XP total
interface/webui/lingo/lingo_vocab.db   # SQLite: SRS cards + review history
```

---

## Development Roadmap

### Phase 1 (Complete)
- [x] v0.1.0 – Simple prototype
- [x] Translation endpoint integration (multi-register)
- [x] Streaming conversation flow (start → respond → stop) with mistake detection and hints

### Phase 2 (Complete)
- [x] Text-to-speech output, with per-user rate limiting
- [x] Adaptive hint system
- [x] Server-side session persistence (streak / XP / difficulty level survive restarts)
- [x] Retry affordances instead of dead-end error states across all screens

### Phase 3 (In progress)
- [x] SM-2 spaced-repetition review flow, backed by SQLite
- [x] Vocabulary auto-extraction from conversation (kanji cache + LLM fallback) feeding directly into the SRS queue
- [x] Integration with Aiko-chan's long-term memory (vocab reviews logged as episodic events)
- [x] Dashboard with XP, streak, due-card count, and a "Practice These" weak-vocab widget
- [ ] Multiplayer leaderboard (real cross-user ranking, not just the requesting user)
- [ ] Voice input (microphone → ASR)
- [ ] Offline vocabulary cache
- [ ] Companion widget (quick translation from homescreen)

---

## Building & Testing

### Debug Build
```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Release Build
```bash
./gradlew assembleRelease
# Requires signing key; configure in local.properties:
# sdk.dir=/path/to/Android/Sdk
# storeFile=/path/to/keystore.jks
# storePassword=***
# keyAlias=***
# keyPassword=***
```

### Unit Tests
```bash
./gradlew test
```

### UI Tests (Espresso)
```bash
./gradlew connectedAndroidTest
```

---

## Integration with Aiko-chan

Aiko-Lingo assumes Aiko-chan is running locally with:
- **FastAPI server** exposing the `/api/english` router (this repo's backend counterpart)
- **Tailscale** configured for secure remote access and session auth
- **Language modules** installed (Japanese NLP/vocab extraction, TTS synthesis; ASR is not yet required since voice input is still planned)

For setup instructions, see [Aiko-chan README](https://github.com/OppaAI/Aiko-chan#quick-start).

---

## Philosophy

Aiko-Lingo embodies the same values as Aiko-chan: **privacy-first, locally deployed, and collaborative**. Language learning is a partnership between human and AI—Aiko-Lingo provides the structure; your effort provides the engagement. Every session, every vocabulary card, and every streak lives on your own server and your own Tailscale network. No cloud, no telemetry, no vendor lock-in.

---

## Contributing

Contributions welcome! Please:
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit with clear messages (`git commit -m "Add X feature"`)
4. Push and open a Pull Request

For architectural decisions or design feedback, open an Issue first to discuss.

---

## License

Apache 2.0 License. See [LICENSE](./LICENSE) for details.

---

## Support & Feedback

- **Issues**: Report bugs or request features via [GitHub Issues](https://github.com/OppaAI/Aiko-Lingo/issues)
- **Discussions**: Chat about design, pedagogy, or ideas in [GitHub Discussions](https://github.com/OppaAI/Aiko-Lingo/discussions)
- **Updates**: Watch the repository for releases and major milestones

---

**Built with ❤ by [OppaAI](https://github.com/OppaAI) · Part of the Aiko ecosystem · Locally deployed, privacy-first, forever open.**
