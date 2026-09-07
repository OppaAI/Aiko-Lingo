# Aiko-Lingo

**An Android companion app for learning Japanese with Aiko—locally deployed, privacy-first.**

> Aiko-Lingo is a native Kotlin/Jetpack Compose application that connects to your local Aiko-chan AI server over Tailscale, providing interactive Japanese language learning through guided conversation, real-time translation, and adaptive difficulty modes. The app exists as a dedicated frontend precisely because language learning requires a distinct interaction model from general-purpose chat.

**Author:** [OppaAI](https://github.com/OppaAI) · Beautiful British Columbia, Canada

[![Repo](https://img.shields.io/badge/Repo-OppaAI%2FAiko--Lingo-967BB6?logo=github&logoColor=white)](https://github.com/OppaAI/Aiko-Lingo)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
![Status](https://img.shields.io/badge/Status-experimental-orange.svg)

**Front:**

**Backend:**
![LLM](https://img.shields.io/badge/Runtime-llama.cpp-967BB6?logo=ai&logoColor=white)
![Python](https://img.shields.io/badge/Python-3.12-blue?logo=python&logoColor=white)
![Ubuntu](https://img.shields.io/badge/Ubuntu-24.04_LTS-orange?logo=ubuntu&logoColor=white)
![CUDA](https://img.shields.io/badge/CUDA-12.6-76B900?logo=nvidia)

---

## Demo

> Click the following image to watch on YouTube ▶

[![Watch the demo]([https://youtu.be/VGHBMMFskCQ](https://www.youtube.com/watch?v=xRtCmtZQgwI))

---

## Features

### Current (Kotlin/Jetpack Compose)
- **Translation Mode** – Translate between English ↔ Japanese with support for:
  - Formal (敬語) and casual (カジュアル) registers
  - Contextual phrasing and grammar notes
- **Conversation Modes** – Guided dialogue with three difficulty levels:
  - **Beginner** – Simple vocabulary, present tense, frequent hints
  - **Intermediate** – Everyday conversation, mixed tenses, selective hints
  - **Advanced** – Nuanced dialogue, cultural context, minimal scaffolding
- **Presentation Styles**
  - Typewriter effect for sequential character reveal
  - Karaoke-mode synchronized highlighting (future)
- **UI Polish**
  - Light "shoujo" theme (soft pastels, rounded corners)
  - Dark "purple glass" theme (frosted morphism, high contrast)
  - Responsive mobile layouts, tested on Firefox Android

### Planned
- **Speech I/O** – Voice input (via cellphone) and audio response (TTS via MioTTS)
- **Spaced Repetition** – Integration with Aiko's memory system for adaptive vocabulary recall
- **User Progress Tracking** – Session history, vocabulary mastery scoring, personalized recommendations
- **Offline Mode** – Cached vocabularies and common phrases for low-connectivity scenarios

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
   - In `app/src/main/res/values/strings.xml` (or your preferences), set the Aiko-chan server URL:
     ```xml
     <string name="aiko_server_url">http://100.x.x.x:8787</string>
     ```
   - Replace `100.x.x.x` with your Aiko-chan device's Tailscale IP

4. **Run:**
   - Select an emulator or physical device
   - Click `Run` (▶) or press `Shift + F10`

---

## Architecture

### Client-Side (This App)
```
MainActivity
├── TranslationScreen
│   ├── InputField
│   └── ResultCard (formatted with register + context)
├── ConversationScreen
│   ├── DifficultySelector
│   ├── DialoguePresentation (typewriter / karaoke)
│   ├── HintButton
│   └── StopButton
└── ThemeController (light / dark purple glass)
```

### Server Contract

Aiko-Lingo communicates with Aiko-chan via RESTful JSON endpoints. All requests include optional headers for user context (passed via Tailscale authenticated session).

#### Translation
```http
POST /api/english/translate
Content-Type: application/json

{
  "text": "Hello, how are you?",
  "register": "formal",
  "context": "greeting"
}

Response:
{
  "original": "Hello, how are you?",
  "japanese": "こんにちは、お元気ですか？",
  "hiragana": "こんにちは、おげんきですか？",
  "register": "formal",
  "notes": "敬語; respectful inquiry after wellbeing"
}
```

#### Conversation (Start)
```http
POST /api/english/conversation/start
Content-Type: application/json

{
  "difficulty": "intermediate",
  "topic": "ordering_food",
  "language": "english"
}

Response:
{
  "session_id": "conv_abc123def456",
  "opening": "いらっしゃいませ！本日のおすすめは天丼です。",
  "opening_english": "Welcome! Today's special is tempura rice bowl.",
  "context": "Restaurant ordering scenario, casual-polite register"
}
```

#### Conversation (Respond)
```http
POST /api/english/conversation/respond
Content-Type: application/json

{
  "session_id": "conv_abc123def456",
  "user_input": "天丼をください。",
  "language": "japanese"
}

Response:
{
  "session_id": "conv_abc123def456",
  "reply": "かしこまりました！こちらです。お召し上がりください。",
  "reply_english": "Understood! Here you go. Please enjoy.",
  "feedback": "Good particle usage (を). Natural ordering phrase.",
  "can_continue": true
}
```

#### Hint
```http
POST /api/english/conversation/hint
Content-Type: application/json

{
  "session_id": "conv_abc123def456",
  "difficulty": "intermediate"
}

Response:
{
  "hint": "You need a sentence particle. Try: [subject] は / が [object] を [verb]",
  "example": "私は天丼を食べたいです。"
}
```

#### Stop
```http
POST /api/english/conversation/stop
Content-Type: application/json

{
  "session_id": "conv_abc123def456"
}

Response:
{
  "summary": "Conversation ended. Vocabulary learned: 5 words. Accuracy: 78%.",
  "learned_words": ["天丼", "召し上がる", "かしこまりました"],
  "accuracy_score": 0.78
}
```

---

## Configuration

### Network
- **Tailscale Integration**: Ensure your Android device is on the same Tailscale network as your Aiko-chan server
- **Server Port**: Default is `8787` (configurable in Aiko-chan settings)
- **HTTPS / SSL**: Not required on Tailscale; traffic is encrypted by default

### Themes
Toggle between themes via **Settings** → **Appearance**:
- **Light (Shoujo)**: Soft pinks, whites, rounded components
- **Dark (Purple Glass)**: Deep purples, semi-transparent cards, high contrast

### Difficulty Tuning
Customize the conversation difficulty curve via `app/src/main/res/values/config.xml`:
```xml
<integer name="beginner_max_sentences">5</integer>
<integer name="intermediate_max_sentences">10</integer>
<integer name="advanced_max_sentences">15</integer>
```

---

## Development Roadmap

### Phase 1 (Current)
- [x] v.0.0.1 - Simple prototype

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
- **FastAPI server** listening on port `8787`
- **Tailscale** configured for secure remote access
- **Language modules** installed (Japanese NLP, TTS, ASR stacks)

For setup instructions, see [Aiko-chan README](https://github.com/OppaAI/Aiko-chan#quick-start).

---

## Philosophy

Aiko-Lingo embodies the same values as Aiko-chan: **privacy-first, locally deployed, and collaborative**. Language learning is a partnership between human and AI—Aiko-Lingo provides the structure; your effort provides the engagement. Every session lives on your device and your Tailscale network. No cloud, no telemetry, no vendor lock-in.

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

MIT License. See [LICENSE](./LICENSE) for details.

---

## Support & Feedback

- **Issues**: Report bugs or request features via [GitHub Issues](https://github.com/OppaAI/Aiko-Lingo/issues)
- **Discussions**: Chat about design, pedagogy, or ideas in [GitHub Discussions](https://github.com/OppaAI/Aiko-Lingo/discussions)
- **Updates**: Watch the repository for releases and major milestones

---

**Built with ❤ by [OppaAI](https://github.com/OppaAI) · Part of the Aiko ecosystem · Locally deployed, privacy-first, forever open.**
