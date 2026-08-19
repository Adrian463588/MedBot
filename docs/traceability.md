# MedBot verification traceability

Matriks ini adalah catatan BMAD/spec-driven untuk `AGENTS.md`, `PRD.md`,
`DESIGN.md`, `docs/MDFILE`, dan referensi AntiSlop. `GoldReference` dan
`reference3` hanya dipakai sebagai referensi pola; keduanya tidak menjadi
sumber data production dan tidak distage.

Status:

- `PASS`: bukti implementasi atau test sesuai kontrak.
- `PARTIAL_PASS`: jalur nyata sebagian terbukti, tetapi acceptance lanjutan belum lengkap.
- `UNAVAILABLE`: aplikasi fail-closed karena capability nyata belum tersedia.
- `BLOCKED`: acceptance membutuhkan device, permission, model, dokumen, foto, atau input yang belum tersedia.

## Kontrak produk

| Requirement | Status | Evidence / boundary |
| --- | --- | --- |
| Kotlin, Compose, Material 3, Hilt, Room, MVVM/UDF | PASS | Source compile, Hilt graph, Room schema/migration, immutable state/event ViewModel, dan lifecycle-aware Flow collection. |
| AntiSlop: content-first, satu primary CTA, minim card, tanpa gradient/emoji/placeholder | PASS (source) | Kontrak resmi ada di [DESIGN.md](../DESIGN.md); UI memakai semantic Material tokens, informative empty/error state, dan action path nyata. |
| Adaptive compact/medium/expanded, list-detail, edge-to-edge | PASS (source); PARTIAL_PASS (physical) | `NavigationBar`, `NavigationRail`, Material Adaptive `ListDetailPaneScaffold`, reflow action row, root insets, dan IME padding tersedia. Physical width/orientation matrix belum seluruhnya dijalankan. |
| Bahasa Indonesia dan English; tanpa Hindi, auth, cloud AI | PASS | `values`/`values-en`; no-auth dan local-only boundary dipertahankan. |
| Tidak ada fabricated/mock/dummy/placeholder production output | PASS (source scan) | Tidak ada canned response, synthetic corpus, pseudo-page, hash embedding fallback, default clinical input, atau benign skin fallback. Fixture hanya berada di test source. |
| Tidak ada `runBlocking`, `GlobalScope`, `!!`, zero-inset void, atau decorative gradient | PASS (production scan) | Scan source Kotlin/res menemukan pola terlarang tidak digunakan; binary PNG dikecualikan dari scan token. |
| Permission dan privacy least-privilege | PASS (source); PARTIAL_PASS (device) | Tidak menambah cloud upload, `RECORD_AUDIO`, hidden phone permission, atau persistent radio identifiers. Camera/notification permission journey belum lengkap. |
| Room migration non-destructive | PASS | Version 2 memakai migration eksplisit 1→2; destructive fallback tidak digunakan. |

## Model, SAF, dan downloader

| Capability | Status | Evidence / boundary |
| --- | --- | --- |
| Official manifest registry | PASS | Empat manifest resmi dipin ke revision immutable dengan HTTPS URL, filename, exact size, SHA-256, provenance, source revision, format, dan capability: Qwen3, Gemma 4, LLaVA-OneVision, InternVL3.5. |
| SAF destination selection | PASS (Samsung API 33) | `ACTION_OPEN_DOCUMENT_TREE`, persistable read/write grant, validasi directory, dan rediscovery menampilkan `SAF folder: MedBotModels` setelah rebuild/reinstall-preserving install. |
| Download writes to SAF | PASS (Samsung API 33) | `Qwen3-0.6B.litertlm.part` terlihat di `/sdcard/Download/MedBotModels`; app-private `filesDir/models` tidak dipakai. |
| Real download progress | PASS (bounded physical) | Progress berasal dari bytes transfer nyata dan speed measured; observed `Downloading 3.4% • 832 KB/s`. |
| Pause/resume | PASS (bounded physical) | Pause mempertahankan partial `17,817,024` bytes; resume melanjutkan ke `23,335,602` bytes tanpa append response `200` ke partial. |
| Full checksum, atomic promotion, model load | PASS (Samsung API 33) | `Qwen3-0.6B.litertlm` di folder SAF berukuran `614236160` byte; SHA-256 device `555579ff2f4fd13379abe69c1c3ab5200f7338bc92471557f1d6614a6e5ab0b4`; UI menampilkan active cache path dan `The local model was initialized.` |
| Vision model download/load | PARTIAL_PASS / UNAVAILABLE | LLaVA vision menghasilkan `.part` nyata `23,368,149` byte dan progress/pause; full artifact dan vision runtime initialization belum tersedia. UI tetap fail-closed dan tidak memakai text-only fallback. |
| Error state | PASS (source + physical) | Invalid SAF tree bug diperbaiki; UI sekarang menampilkan error actionable dan Retry hanya pada WorkInfo failure nyata. |

## Feature matrix

| Feature / screen | Real control path | Status |
| --- | --- | --- |
| Home | Model/document readiness nyata, one primary consultation CTA, secondary feature links | PASS (source + Samsung smoke) |
| Chat | Session list/detail, new/delete/back, labelled composer, attachment picker, citation dialog, model gate | PARTIAL_PASS; model lokal Qwen sudah loaded, tetapi response journey belum diklaim karena input automation pada device berpindah ke task pihak ketiga. |
| Skin Scan | Camera/gallery, bounded media copy, required body part/notes, analyze gate | PARTIAL_PASS; input path tersedia, vision UNAVAILABLE tanpa vision runtime |
| Skin Lineage | Empty/list-detail, record selection/delete/back | PARTIAL_PASS; no real vision result supplied |
| Knowledge Base | SAF import, real parser/provenance/checksum, search/delete, embedder gate | PARTIAL_PASS; real document/embedder journey BLOCKED |
| Model Manager | Folder/file picker, backend, download/pause/resume/cancel/retry/load/unload/delete, truthful errors | PASS (Qwen path); PARTIAL_PASS (vision path); SAF, transfer, pause/resume, checksum, promotion, dan LiteRT load terbukti untuk Qwen. |
| Persona | Agent/language/tone/depth/profile/instructions and save via ViewModel/DataStore | PASS (source + Samsung smoke) |
| Medical Tools | Drugs/labs/calculators/reminders tabs, explicit inputs, validation and unavailable state | PARTIAL_PASS; explicit input validation dan IME last-action visibility terbukti pada Samsung large text, reference-dependent data remains UNAVAILABLE. |

## Static, unit, dan instrumentation evidence

| Gate | Result | Evidence |
| --- | --- | --- |
| `git diff --check` | PENDING FINAL STAGE | Run again after documentation and staged allowlist are complete. |
| `./gradlew lintDebug --rerun-tasks` | PASS | No lint errors; report at `app/build/reports/lint-results-debug.html`. |
| `./gradlew testDebugUnitTest --rerun-tasks` | PASS | 55 tests, 1 skipped, 0 failures/errors; manifest/protocol, downloader validation, triage, agents, calculators, parser/chunker, and verification contracts. |
| `./gradlew assembleDebug` | PASS | `assembleDebug --rerun-tasks --no-daemon` completed; final debug APK path is `app/build/outputs/apk/debug/app-debug.apk`. |
| `./gradlew connectedDebugAndroidTest` | PASS (bounded) | `EvidenceGateComposeTest` passed on Samsung SM-G988B API 33; broader interaction matrix remains pending. |

## Physical device evidence

| Device | Result | Observed |
| --- | --- | --- |
| Samsung `RRCN3008VYE`, SM-G988B, API 33, 1440×3200 | PARTIAL_PASS | Latest APK installed; launch/MainActivity, Home, Model Manager, SAF folder persistence, real Qwen3 transfer, pause/resume, large-text layout, and logcat error diagnosis verified. No MedBot FATAL EXCEPTION/ANR match in bounded scan. |
| Xiaomi `QSWSEMRKNFZ9LJRC`, API 35, 1220×2712 | BLOCKED | Serial absent from `adb devices -l`; no physical claim is made. |

Evidence:

- [Home live preview](e2e/preview/medbot_home_final-2026-08-19-samsung.png)
- [Model Manager loaded preview](e2e/preview/medbot_model_loaded_final-2026-08-19-samsung.png)
- [Paused partial preview](e2e/preview/medbot_model_paused-2026-08-19-samsung.png)
- [Medical Tools IME preview](e2e/preview/medbot_final_tools_ime2-2026-08-19-samsung.png)
- [Vision download preview](e2e/preview/medbot_vision_downloading-2026-08-19-samsung.png)
- [Device UI dumps](e2e/device/)
- [JSON journey report](e2e/reports/medbot-e2e-2026-08-19.json)

Not yet physically verified: full 320/360/411/600/840+ dp matrix in both orientations, TalkBack target audit, airplane mode, permission revoke/re-authorize, full vision checksum/load/runtime, RAG with a real document/embedder, camera with a real photo, and an end-to-end chat response. Those states remain `BLOCKED` or `UNAVAILABLE`. Qwen text-model checksum and load are physically verified; they are not generalized to vision.

## Delivery boundary

Only intentional MedBot files are eligible for staging. Nested
`docs/REFERENCES/GoldReference`, `docs/REFERENCES/reference3`,
`.gradle/buildOutputCleanup/*`, and unrelated reference changes remain
excluded. Before push: review staged allowlist, run secret scan, commit on
`main`, push without force, and compare local `HEAD` with remote
`refs/heads/main`.
