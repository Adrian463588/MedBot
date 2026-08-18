# MedBot

MedBot adalah aplikasi Android Kotlin + Jetpack Compose untuk informasi
kesehatan local-first. Triase keselamatan, persona, kalkulator deterministik,
penyimpanan chat, parser dokumen, dan state UI berjalan di perangkat. Inferensi
LLM/RAG/vision hanya boleh berjalan setelah runtime dan input nyata melewati
validasi evidence gate.

MedBot tidak memiliki auth, Hindi, cloud AI, telemetry medis, diagnosis
otomatis, katalog klinis sintetis, response canned, placeholder feature, atau
output fabricated. Aplikasi adalah alat bantu informasi, bukan dokter,
layanan darurat, atau pengganti tenaga kesehatan.

## AntiSlop design contract

[DESIGN.md](DESIGN.md) adalah kontrak UI/UX resmi yang dirumuskan dari
`docs/AntiSlop/Reference1.md` dan `Reference2.md`, lalu dibatasi oleh
`AGENTS.md` dan `PRD.md`.

Kontrak visual memakai Material 3 semantic roles, typography yang jelas,
spacing `4/8/16/24/32/48dp`, radius terbatas, tonal surface tanpa gradient,
content-first hierarchy, satu primary CTA per screen, dan meaningful empty /
loading / error / unavailable state. Tidak ada card-soup, emoji decoration,
fake avatar, stock illustration, `TextField.placeholder`, atau constant motion.

Layout adaptive memakai `NavigationBar` pada compact, `NavigationRail` pada
medium/expanded, serta list-detail pane untuk Chat, Knowledge Base, dan Skin
Lineage pada window expanded. Pada compact/medium, detail pane reflow menjadi
single pane agar selected content tetap dapat dibaca. Root `Scaffold` mengelola
edge-to-edge insets; Material 3 `1.4.0` dan stable Adaptive `1.2.0` dipakai
karena metadata Adaptive `1.3.0` belum kompatibel dengan AGP/compileSdk lokal.

## Arsitektur

```text
Compose content + lifecycle-aware StateFlow
        -> ViewModel + sealed UI event
        -> domain use case / pure policy
        -> repository or platform gateway
        -> Room, DataStore, SAF, LiteRT-LM, Android APIs
        -> typed result / unavailable state
```

- `domain/`: model, triage, registry agent, calculator, policy, dan interface.
- `data/`: Room/DataStore, parser PDF/TXT/MD/DOCX, downloader, runtime adapter.
- `presentation/`: ViewModel, immutable UI state/event, navigation, Compose.
- `core/`: Hilt composition root, localization, Material 3 design system.

ViewModel tidak menyimpan Activity/View. Compose tidak mengakses DAO, runtime,
WorkManager, atau service mentah. Room memakai migration eksplisit tanpa
destructive fallback. Backup mengecualikan chat, foto kulit, dokumen RAG,
model, database, dan preference sensitif.

## Feature and acceptance status

| Area | Status | Batas evidence |
| --- | --- | --- |
| Kotlin/Compose/Material 3/MVVM/UDF/Hilt/Room | PASS (build/source) | Lint, unit, assembly, dan connected evidence gate lulus. |
| Home, Chat, Skin, Lineage, Knowledge, Models, Persona, Tools | PARTIAL | UI/state paths dan Samsung smoke tersedia; full real-data journeys tetap dibatasi prerequisite. |
| Bahasa Indonesia dan English | PASS (source) | Resource `values` dan `values-en`; Hindi di luar scope. |
| Triase dan 46-agent registry | PASS (unit) | Registry/policy test lulus; inference model-dependent terpisah. |
| Input calculator eksplisit | PASS (unit/domain) | Blank, malformed, dan out-of-range ditolak; tidak ada default klinis. |
| LiteRT-LM chat | BLOCKED | Tidak ada `.litertlm` dengan metadata resmi URL/size/SHA-256/backend terverifikasi; registry sengaja kosong. |
| RAG dokumen nyata | BLOCKED | Parser dan provenance tersedia, tetapi user document dan embedder lokal tervalidasi belum tersedia. |
| Skin vision | UNAVAILABLE | Foto/readability divalidasi; model vision lokal belum diinisialisasi sehingga tidak ada diagnosis/benign baseline. |
| Samsung API 33 | PARTIAL PASS | Launch, navigation, empty/unavailable checkpoints, Chat reflow, UI dumps, dan exact MedBot crash/ANR filter. |
| Xiaomi API 35 | BLOCKED | Serial tidak muncul pada `adb devices -l`. |

Detail matriks requirement dan evidence ada di
[docs/traceability.md](docs/traceability.md).

## Local-only and fail-closed boundary

Model manager menerima `.litertlm` melalui SAF. Model baru berstatus loaded
setelah file, extension, ukuran, checksum bila diwajibkan, backend, dan
inisialisasi LiteRT-LM valid. Downloader hanya boleh memakai manifest resmi
HTTPS dengan ukuran dan SHA-256; karena manifest tersebut belum ada,
`ModelRegistry.OFFICIAL_MODELS` tetap kosong.

Knowledge Base hanya menerima file yang dipilih pengguna melalui SAF. Parser
mempertahankan checksum SHA-256 dan provenance asli; page number tidak dibuat
untuk format yang tidak memilikinya. Embedding unavailable menghasilkan
`EMBEDDER_UNAVAILABLE`, bukan vector hash. Foto kulit disimpan lewat gateway
app-private yang bounded, tetapi analisis tetap `UNAVAILABLE` tanpa model
vision lokal nyata. Semua nilai klinis harus dimasukkan eksplisit.

## Build and test

PowerShell pada Windows:

```powershell
.\gradlew.bat lintDebug --rerun-tasks --no-daemon --console=plain
.\gradlew.bat testDebugUnitTest --rerun-tasks --no-daemon --console=plain
.\gradlew.bat assembleDebug --rerun-tasks --no-daemon --console=plain
```

Dengan device/emulator yang benar-benar terdeteksi:

```powershell
adb devices -l
.\gradlew.bat connectedDebugAndroidTest --no-daemon --console=plain
```

Evidence terakhir pada 2026-08-19:

- lint: PASS;
- unit: PASS, 44 test cases, 1 skipped, 0 failures;
- assemble: PASS;
- connected: PASS, 1 evidence-gate test pada Samsung `SM-G988B - 13`;
- source/build evidence dan physical/model/data acceptance dilaporkan terpisah.

## Physical live preview

Samsung `RRCN3008VYE`, SM-G988B, API 33, 1440×3200:

![MedBot live preview — Samsung API 33](docs/e2e/preview/medbot_live_preview-2026-08-19-samsung.png)

Checkpoint lain dan UI dump tersedia di [docs/e2e/preview](docs/e2e/preview),
[docs/e2e/device](docs/e2e/device), dan
[docs/e2e/reports/medbot-e2e-2026-08-19.json](docs/e2e/reports/medbot-e2e-2026-08-19.json).
Screenshot sample RAG, catalog stale, atau aplikasi lain tidak digunakan
sebagai acceptance.

## Privacy and safety

Tidak ada request cloud AI atau upload data medis. Jangan masukkan rahasia atau
identitas pihak lain. Jangan gunakan aplikasi untuk keadaan gawat darurat,
diagnosis, resep, atau keputusan terapi tanpa tenaga kesehatan. Bila model,
embedder, dokumen, foto, permission, atau input nyata belum tersedia, aplikasi
harus mempertahankan state `BLOCKED`, `UNAVAILABLE`, atau
`INSUFFICIENT_DATA`.
