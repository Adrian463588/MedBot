# MedBot: On-Device Medical AI Assistant

A privacy-first, fully offline, on-device medical AI assistant built with Android Jetpack Compose.

![Live Preview](HealthApp.png)

## Features
- **Zero-Friction**: No auth, no cloud APIs, 100% private.
- **Dual-Mode AI Models**: Support for loading `.litertlm` and `.gguf` from local SAF or downloading directly via the app.
- **On-Device RAG**: Ingest local PDFs, convert to semantic chunks, and retrieve them via local embeddings using Room DB vector search.
- **Skin Lineage**: Track skin conditions over time with timeline comparisons using CameraX and ABCD rules.
- **Multi-Agent System**: 46 distinct medical agents with custom personas based on standard clinical practices.

## Architecture
- Clean Architecture (Domain, Data, Presentation)
- Jetpack Compose + Material 3 Theme
- Dagger Hilt for DI
- Room Database for chat history, document vectors, and skin lineage records
- WorkManager + OkHttp for resumable background model downloads
- `llama.cpp` / LiteRT GenAI for local inference

## Getting Started
1. Open this project in Android Studio (Koala or later).
2. Sync Gradle dependencies.
3. Run on a physical Android device (API 26+).
4. Download the `Gemma 2B` or `Gemma 4B` model from the Model Manager screen to start chatting.

## Acknowledgements
Designed in accordance with `AGENTS.md` and `PRD.md`.
