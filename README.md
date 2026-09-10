# Aiko-Lingo

**An Android companion app for learning Japanese with Aiko—locally deployed, privacy-first.**

> Aiko-Lingo is a native Kotlin/Jetpack Compose application that connects to your local Aiko-chan AI server over Tailscale, providing interactive Japanese language learning through JLPT-tracked vocab decks, grammar decks, review sessions, typing practice, guided conversation, and real-time translation. The app exists as a dedicated frontend precisely because language learning requires a distinct interaction model from general-purpose chat.

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

[![Aiko-Lingo Demo](https://img.youtube.com/vi/65BfSeToR-Y/maxresdefault.jpg)](https://www.youtube.com/watch?v=65BfSeToR-Y)

---

## Features

### Current (Kotlin/Jetpack Compose + FastAPI backend)

- **Vocab 🌱** – One lesson at a time, JLPT-tracked:
  - JLPT track selector (N5 → N1) with earned progression
  - Kana decks drill 10 random cards per round (Shuffle ↻) with tap-to-reveal flashcards and 🔊 per-card pronunciation
  - Words & Phrases: 10 fresh random pool words in the same flashcard format
  - Current lesson card → full flashcard screen; per-lesson typing **Test** (every card, random order, 100% to advance); level **Final Test** (up to 100 sampled questions, 100% auto-levels-up your JLPT)
- **Grammar ✏️** – Same study flow as Vocab: one deck at a time (basics first, then OpenJLPT), per-pattern 🔊 (pattern markers like 〜 are never spoken), typing tests per deck plus a level final
- **Review 🔁** – One session = 10 random learnt cards (SRS + spawn pool + JLPT vocab **and** grammar) as multiple-choice questions. Cards may repeat across sessions by chance. Every answer is graded through SM-2, so scheduling, streak, and XP keep working; a failed submit keeps you on the card with a retry toast instead of wiping the session
- **Practice ✍️** – Typing practice over the same mixed pool: 10 random cards, one at a time. Hear the word, type the Japanese (keyboard, handwriting/draw, or voice — all via your system IME), Send to check. Match → next card; 5 XP per correct answer; grammar patterns accept the bare form (no 〜 needed)
- **Level gating** – Everything (vocab, review, practice, grammar, courses) is filtered to your JLPT level or lower: N5 sees only N5, N4 sees N5+N4, and so on. Never anything above your level
- **Translation Mode** – Auto-direction: any non-Japanese input (English, Chinese, French, …) → Japanese in 3–5 registers (formal 敬語, casual カジュアル, points in between) in one LLM call; Japanese input → a single natural English translation. 🔊 playback is offered only for Japanese text (the TTS voice is Japanese-only); repeat translations served instantly from cache
- **Study Reminders 🔔** – An hourly nudge at the top of the hour while you haven't studied that day (WorkManager + notification channel, granted via the standard Android 13+ permission prompt). Any real study action — a review answer, a finished practice, a test submit, a translation, a chat turn — silences reminders for the day; they re-arm automatically after midnight, and reschedule after reboot
- **Conversation Mode** – Guided dialogue with three difficulty levels (beginner / intermediate / advanced):
  - Streaming replies with a typewriter-style karaoke presentation
  - Live mistake detection: incorrect turns surface English feedback plus a corrected Japanese suggestion
  - Contextual hints on demand
  - A non-streaming `/conversation/respond` endpoint alongside the streaming one, for callers that prefer a single round trip
- **Spaced Repetition (SRS)** – Vocabulary encountered in conversation is automatically extracted (kanji-cache lookup with an LLM fallback for anything uncommon), JLPT-tagged at learn time, and scheduled with a real SM-2 algorithm. Day-one users get starter N5 cards auto-seeded so Review works immediately
- **Progress Tracking** – Dashboard with JLPT level badge 🎌, XP & levels, day-over-day streaks, total/learned/due card counts, average ease, Word of the Day (fixed per user per day), and a "Review Weak Spots" widget — all persisted to disk so nothing resets on a server restart. Conversation turns, reviews, learn marks, and practice sessions all contribute to XP and streak
- **Leaderboard** – Your rank, XP, and streak display (single-player personal progress)
- **Toast Notifications** – Server-driven toasts ("Perfect! Let's keep talking.", level-start greetings, "🌟 word = meaning" on an Easy-graded review) surfaced consistently across the Conversation, Translate, and Review screens
- **Resilience & Speed** – Cache-first screens (revisits render instantly from `LingoCache` while a background refresh updates), short 20–25s timeouts on JSON calls so failures show Retry fast (120s kept only for LLM streaming), retry affordances on every screen instead of dead-end error states; TTS is rate-limited per user with a real `429` response instead of a silent failure
- **UI Polish**
  - Light "Shoujo" theme (light-pink background, soft pastels, rounded corners) with a compact type scale that fits small phone screens
  - Dark "Lavender Glass" theme (deep purples, high contrast)
  - 8 distinct menu-card hues; theme choice survives rotation

### Planned (see [Roadmap](#development-roadmap) for build order)

- **Persisted Offline Cache** – Room-backed lesson/weak-vocab/Word-of-the-Day so study screens open with zero network (beyond today's in-memory `LingoCache`)
- **Private Voice Input** – One-tap mic → clip goes over Tailscale to Aiko-chan's on-device ASR (sherpa-onnx, no cloud), transcript lands in the input. Until then, voice already works via the system keyboard's 🎤 mode, plus ✍️ handwriting the same way
- **Companion Widget** – Quick translation from the homescreen
- **Session Persistence Across Devices** – Per-user state already survives app/backend restarts on the server; a future pass will let it sync across multiple client devices for the same user

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
    - No code changes needed: open the app, tap the **🔗 server row** under the main menu, and enter your Aiko-chan's Tailscale hostname (`https://<tailscale-hostname>/`) or Tailnet IP (`http://100.x.y.z:8787/`). Saving reconnects instantly; the choice persists across restarts.
    - Tailscale handles TLS termination and auth, so plain `https://<tailscale-hostname>/` is normal here — you don't need your own certificate. (Cleartext is allowed for Tailnet IPs only; the WireGuard transport underneath stays encrypted.)
    - The backend must expose the `/api/nihongo` lingo router; audio URLs are built from the address you actually connect with, so hostnames, IPs, and funnel URLs all just work. Server operators can also pin the public origin via the `AIKO_PUBLIC_BASE_URL` env var (falls back to `REDIRECT_BASE`).
    - Developers: the default is `ServerConfig.DEFAULT_URL`; Retrofit clients are built from the stored value in `MainActivity.kt`.

4. **Run:**
   - Select an emulator or physical device
   - Click `Run` (▶) or press `Shift + F10`

---

## Architecture

### Client-Side (This App)
```
MainActivity (short-TTL JSON client + long-TTL streaming client)
├── MainMenu (Vocab / Grammar / Review / Practice / Chat / Translate / Stats / Ranks)
├── VocabScreen              (VocabViewModel)
│   ├── JLPT Track (N5→N1, locked progression) + progress bar
│   ├── Kana decks (random-10 + Shuffle ↻) + Words & Phrases flashcards
│   ├── Current-lesson cards → lesson screen / typing Test screen / Final
│   ├── FlashcardViewer + per-card 🔊 TTS (stale-tap guarded MediaPlayer)
│   └── LingoCache (instant revisits, background refresh)
├── GrammarScreen            (CoursesViewModel, mode=grammar)
│   ├── Current-deck cards → deck screen / typing Test screen / Final
│   └── 🔊 skips 〜 markers (〜です speaks as です)
├── LessonTestScreen         (LessonTestViewModel, track=vocab|grammar)
│   ├── One input per question (whole lesson / ≤100 final), random order
│   └── Result → misses review → next lesson / level-up
├── ReviewScreen             (ReviewViewModel)
│   ├── 10 random cards (SRS + spawn + JLPT vocab + grammar), SM-2 grading
│   ├── Submit guard + failure toast (session survives network blips)
│   └── Finished hub → Vocab / Practice (dedicated empty state included)
├── PracticeScreen           (PracticeViewModel)
│   ├── Type-the-answer + 🔊 + IME (⌨️ ✍️ 🎤), 5 XP per correct
│   ├── 〜-tolerant matching, Empty state when the pool is dry
│   └── Finished → score + XP + retry
├── TranslateScreen          (TranslateViewModel, result cache)
│   └── 🔊 only on Japanese text (TTS voice is Japanese-only)
├── ConversationScreen       (ConversationViewModel, streaming client)
│   ├── LevelSelection
│   ├── DialogueBubble / KaraokeBubble (typewriter streaming)
│   ├── ResponseInput + Hint button
│   └── Toast overlay (server-driven)
├── DashboardScreen          (DashboardViewModel)
│   ├── JLPT badge 🎌, XP / Streak / Stats cards, Word of the Day
│   ├── "Review Weak Spots" widget → ReviewScreen
│   └── Stats-first loading (word arrives async, stale-refresh guarded)
└── LeaderboardScreen        (LeaderboardViewModel, cached)
```

### Server-Side (Aiko-chan, FastAPI router at `/api/nihongo`)
```
interface/webui/lingo/
├── router.py      — HTTP routing, streaming, audio, streaks/XP/level bookkeeping,
│                     JLPT gating, review/practice session sampler, courses/grammar
├── learn_api.py   — /learn/* (level-filtered pool + status)
├── spawn.py       — hourly pre-spawned shared vocab pool (never blocks requests)
├── lessons.py     — kana decks + lesson pool (consolidated into materials.db)
├── lingo_store.py — storage paths: shared materials.db + per-user agentic/lingo.db
├── srs.py         — SM-2 scheduler over per-user SQLite (level-tagged cards)
├── vocab.py       — Japanese tokenization, kanji lookup (+ LLM fallback), romaji
└── models.py      — Pydantic request/response schemas
```

LLM inference happens only in Chat, Translate, vocab-extraction fallback, and background pool spawning — Review/Practice/Stats/courses are pure SQLite. See `interface/webui/lingo/README.md` in Aiko-chan if present.

All endpoints authenticate via the caller's session (Tailscale-authenticated), not a client-supplied user ID — this keeps one user's SRS cards, streak, and XP from ever mixing with another's.

### Server Contract

All endpoints live under `/api/nihongo` and rely on session auth rather than a request-body user/session identifier. Conversation continuity (the "session") is client-side: the app resends the running dialogue history with each turn rather than the server tracking a `session_id`.

#### JLPT Level
```http
GET /api/nihongo/level            → { "level": "N5", "levels": ["N5","N4","N3","N2","N1"] }
POST /api/nihongo/level           { "level": "N4" }
```
All content endpoints below filter to the caller's level **or lower** (N4 sees N5+N4).

#### Vocab — Learn Pool
```http
GET /api/nihongo/learn/new        → { "items": [{ "pool_id": 7, "front": "ねこ", "back": "cat", "reading": "ねこ", "kind": "kanji", "level": "N5" }], "level": "N5", "pending_in_pool": 42 }
POST /api/nihongo/learn/mark      { "items": [{ "pool_id": 7 }] }  → { "learned": 1, "xp": 5 }
GET /api/nihongo/learn/status     → { "level": "N5", "levels": [...], "pool_size_at_level": 42, "pool_size_total": 128, "user_cards": 10, "reviews_today": 0, "learned_today": 3 }
```
Only `pool_id`s are trusted on mark — client-side front/back text is ignored.

 #### Vocab — Lesson Decks & Courses
```http
GET /api/nihongo/lessons          → [{ "id": "hiragana", "title": "Hiragana", ... }]
GET /api/nihongo/lessons/{deck_id}
GET /api/nihongo/courses          → [{ "id": "openjlpt-n5-lesson-1", "title": "N5 Lesson 1 (OpenJLPT)", "level": "N5", "kind": "course", "card_count": 12 }]
GET /api/nihongo/courses/{course_id}
GET /api/nihongo/grammar
GET /api/nihongo/grammar/{grammar_id}
```
Lists return `[]` when empty (never 404); only unknown detail ids 404 — including ids above your JLPT level.

#### Lesson Progression — One At A Time + Typing Tests + Finals
Same shape under `/courses` (vocab) and `/grammar` (grammar decks):
```http
GET /api/nihongo/courses/current
→ { "progress": { "level": "N5", "track": "vocab", "current_lesson": 1,
                  "lessons_total": 56, "final_unlocked": false },
    "lesson": { "id": "openjlpt-n5-lesson-1", "title": "…", "cards": […] } }

GET /api/nihongo/courses/{course_id}/test
→ { "deck_id": "…", "title": "…", "questions": [{ "qid": "c0", "prompt": "cat" }, …] }

POST /api/nihongo/courses/{course_id}/test/submit
{ "answers": [{ "qid": "c0", "answer": "ねこ" }] }
→ { "correct": 12, "total": 12, "passed": true, "xp": 24,
    "results": [{ "qid": "c0", "prompt": "cat", "expected": ["ねこ"], "given": "ねこ", "correct": true }, …],
    "progress": { … }, "new_level": null }
```
Lesson tests cover the whole deck in random order and need 100% to advance (answer matching is server-side: reading/front, case/space-insensitive, 〜-tolerant, grammar gloss-tolerant). Final variants (`GET …/final/test?n=100`, `POST …/final/submit`) sample up to 100 cards across the level; a passed final auto-levels-up your JLPT (`new_level`). Tested cards enter your SRS queue, and every submit records streak + XP.

#### Review — Random 10-Card Session
```http
GET /api/nihongo/review/session?n=10
→ [{ "card_id": 4, "hiragana": "たべる", "meaning": "to eat", "context": "", "kanji": "食べる" }]
```
Random learnt cards at/below your level (SRS + spawn pool + JLPT vocab + grammar; repeats across sessions allowed); tops up from shared materials when your learnt pool is short. Grade each card with the existing respond endpoint:
```http
POST /api/nihongo/conversation/review/respond
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

#### Practice — Typing Session
```http
GET /api/nihongo/practice/session?n=10   (same card shape as review/session)
POST /api/nihongo/practice/mark          { "correct": 8, "total": 10 }  → { "xp": 40, "correct": 8, "total": 10 }
```
5 XP per correct card. Answer matching is client-side (hiragana or kanji, case/space-insensitive).

#### Translate
Auto-direction: Japanese input → one English translation; anything else → Japanese registers.
```http
POST /api/nihongo/translate
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
Japanese input instead returns exactly one item (`{ "register": "English", "text": "…" }`) — registers make no sense for English output.

#### Conversation — Start (streaming, `text/event-stream`)
```http
POST /api/nihongo/conversation/start
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
POST /api/nihongo/conversation/respond_stream
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

A non-streaming counterpart, `POST /api/nihongo/conversation/respond`, returns the equivalent payload as a single JSON response for callers that don't want SSE.

#### Conversation — Hint
```http
POST /api/nihongo/conversation/hint

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
POST /api/nihongo/conversation/stop

Response: { "success": true }
```

#### Text-to-Speech
```http
GET /api/nihongo/tts?text=こんにちは

Response: { "audioUrl": "https://.../lingo_audio/....wav" }
```
Rate-limited per user (20 requests/minute); returns `429` when the limit is hit and `503` only on genuine synthesis failure.

#### Stats / XP / Word of the Day / Leaderboard
```http
GET /api/nihongo/stats
GET /api/nihongo/weak-vocab
GET /api/nihongo/word-of-day        (fixed per user per calendar day, server-cached)
GET /api/nihongo/xp
POST /api/nihongo/xp/add        { "amount": 10 }
GET /api/nihongo/leaderboard
```

---

## Configuration

### Network
- **Tailscale Integration**: Ensure your Android device is on the same Tailscale network as your Aiko-chan server
- **Server Port**: Default is `8787` (configurable in Aiko-chan settings); the FastAPI router itself is mounted under `/api/nihongo` regardless of port
- **HTTPS / SSL**: Not required on Tailscale; traffic is encrypted by default
- **Timeouts**: JSON calls use 20–25s timeouts (fail fast to Retry); conversation streaming keeps 120s for long LLM turns

### Themes
Toggle between themes via the "✨ Switch Aesthetic ✨" button on the main menu (choice survives rotation):
- **Light (Shoujo)**: Light-pink background, soft pastels, rounded components, compact type
- **Dark (Lavender Glass)**: Deep purples, semi-transparent cards, high contrast

### Server-Side Persistence
Per-user state is written to disk under `USER_SPACE_ROOT/<user_id>/` so it survives restarts:
```
agentic/lingo/vocab.db    # SQLite: level-tagged SRS cards + review history (per user)
agentic/lingo.db          # SQLite: level / XP ledger / streaks / JLPT progress (per user)
data/word_of_day/{user_id}.json   # today's cached Word of the Day
interface/webui/lingo/materials.db  # SQLite (shared, versioned): JLPT cards, courses,
                                     grammar decks, pre-spawned vocab/lesson pools
```
Legacy files (`data/streaks|levels|xp/*.json`, `lingo_vocab.db`, `vocab_pool.db`, `lesson_pool.db`) are auto-migrated into the above on first run.

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

### Phase 3 (Complete)
- [x] SM-2 spaced-repetition review flow, backed by SQLite
- [x] Vocabulary auto-extraction from conversation (kanji cache + LLM fallback) feeding directly into the SRS queue
- [x] Integration with Aiko-chan's long-term memory (vocab reviews logged as episodic events)
- [x] Dashboard with XP, streak, due-card count, and a "Practice These" weak-vocab widget
- [x] JLPT track (N5→N1) with earned progression, level-gated content, and per-user SQLite storage
- [x] Vocab hub (decks + courses + pronunciation), Grammar decks, typing Practice with XP
- [x] Cache-first screens + split network timeouts for instant revisits

### Phase 4 (Complete)
- [x] Lesson-at-a-time study flow: one vocab lesson / grammar deck at a time, per-lesson typing tests (100% to advance), level finals (100% auto-levels-up JLPT)
- [x] Review/Practice draw from SRS + spawn pool + JLPT vocab + grammar
- [x] Translate auto-direction (non-Japanese → Japanese registers; Japanese → single English) with Japanese-only TTS buttons
- [x] Hourly study reminders (top-of-hour nudge while idle that day; silent after studying; re-arms at midnight; survives reboot)

### Phase 5 (Proposed — in recommended build order)
- [ ] Persisted offline vocabulary cache (Room: bundle current lesson + weak vocab + Word of the Day so study screens open with zero network; sync on refresh) — biggest daily-use win, no backend changes
- [ ] Private voice input (one-tap mic → Aiko-chan's on-device sherpa-onnx ASR over Tailscale; no cloud, no new phone permission model beyond the mic prompt)
- [ ] Companion widget (homescreen quick-translate; small Glance widget reusing `/translate` + TTS)

---

## Building & Testing

### Debug Build
```bash
./gradlew assembleDebug --offline   # offline works once dependencies are cached
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
- **FastAPI server** exposing the `/api/nihongo` router (this repo's backend counterpart)
- **Tailscale** configured for secure remote access and session auth
- **Language modules** installed (Japanese NLP/vocab extraction, TTS synthesis; ASR is not yet required since voice input currently goes through the system IME)

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
