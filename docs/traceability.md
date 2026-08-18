# MedBot verification traceability

Dokumen ini adalah matriks BMAD/spec-driven untuk `AGENTS.md`, `PRD.md`,
`DESIGN.md`, `docs/MDFILE`, dan referensi AntiSlop. GoldReference serta
reference3 hanya dipakai sebagai pola pembelajaran; keduanya bukan sumber
data production dan bukan bukti acceptance.

Status yang dipakai:

- `PASS`: bukti source, test, build, atau device sesuai batas yang ditulis.
- `PARTIAL`: sebagian kontrak terbukti, tetapi ada acceptance lanjutan yang belum tersedia.
- `UNAVAILABLE`: jalur aplikasi fail-closed karena dependency nyata belum tersedia.
- `BLOCKED`: pengujian tidak dapat dilakukan tanpa device, permission, model, dokumen, foto, atau input nyata.

## Kontrak dan batas produk

| Requirement | Status | Evidence / batas |
| --- | --- | --- |
| Kotlin, Compose, Material 3, Hilt, Room, MVVM/UDF | PASS | Source build dan dependency graph berhasil; screen memisahkan state/event ViewModel dari content Compose. |
| AntiSlop: content-first, satu primary CTA, tanpa card-soup/gradient/emoji/placeholder | PASS (source) | Kontrak resmi ada di [DESIGN.md](../DESIGN.md); production UI memakai token semantic, empty state, dan action handler nyata. |
| Adaptive compact/medium/expanded dan edge-to-edge | PARTIAL | `NavigationBar`, `NavigationRail`, wrapping action row, `ListDetailPaneScaffold`, safe drawing insets, dan compact reflow tersedia. Physical width matrix 320/360/411/600/840+ belum seluruhnya tersedia. |
| Indonesian dan English; tanpa Hindi/auth/cloud AI | PASS | Resource `values` dan `values-en`; no-auth/local-only boundary dipertahankan. |
| Tanpa fabricated/mock/dummy/placeholder production output | PASS (source audit) | Tidak ada canned response, synthetic corpus, pseudo-page, hash embedding fallback, default clinical input, atau benign skin fallback. Fixture hanya berada di test source. |
| Tidak ada `runBlocking`, `GlobalScope`, `!!`, zero-inset void, atau gradient decoration | PASS (source/lint) | Scan production Kotlin dan lint final bersih untuk pola tersebut. `hashCode()` pada `DocChunk` hanya untuk equality ID, bukan checksum. |
| Permission dan privacy least-privilege | PASS (source) | Tidak menambah `RECORD_AUDIO`, hidden phone permission, cloud upload, atau raw persistent radio identifier. Device permission matrix belum penuh. |
| Room migration non-destructive | PASS | Database version 2 memiliki migration eksplisit 1→2; `fallbackToDestructiveMigration` tidak digunakan. |

## Feature matrix

| Feature / screen | UI and real control path | Status |
| --- | --- | --- |
| Home | Status model/dokumen nyata, satu CTA konsultasi, links ke feature screens, adaptive action row | PASS (source + Samsung smoke) |
| Chat | Session baru/pilih/hapus, selected detail, labelled composer, attachment picker, send, citation dialog, persona, back; local model gate | PARTIAL — UI path PASS; inference `BLOCKED` tanpa `.litertlm` tervalidasi |
| Skin Scan | Camera/gallery, bounded app-private copy, body-part wajib, notes, analyze, error/unavailable state | PARTIAL — input path PASS; vision `UNAVAILABLE` tanpa model vision lokal |
| Skin Lineage | Empty/list-detail, selected record, before/latest comparator, delete/back | PARTIAL — empty/list path PASS; no real photo/vision result supplied |
| Knowledge Base | SAF import, real parser, SHA-256, provenance/page metadata, search/delete, empty/error state | PARTIAL — empty/SAF path PASS; real embedder/document journey `BLOCKED` |
| Model Manager | `.litertlm` picker, backend choice, load/unload/delete, progress/pause/resume/cancel paths, fail-closed status | UNAVAILABLE — official registry empty and no user model supplied |
| Persona | Agent search, language/tone/depth/profile/instruction fields, save/back through ViewModel | PASS (screen/device smoke); model behavior remains separate acceptance |
| Medical Tools | Drugs/labs/calculators/reminders tabs, explicit inputs, validation, unavailable catalog/reference states | PARTIAL — deterministic validation PASS; catalog-dependent tools `UNAVAILABLE` |

## Control and navigation matrix

| Control family | Implementation | Evidence boundary |
| --- | --- | --- |
| Root navigation | `NavigationBar` on compact; `NavigationRail` on medium/expanded; `launchSingleTop` and root `popUpTo` | Samsung navigation smoke; expanded/landscape physical run pending |
| Back | Route back; compact list-detail back clears selected entity before route back | Chat screenshot/UI dump confirms selected detail and composer on Samsung |
| SAF document/model picker | Activity Result launcher -> ViewModel -> bounded app-private gateway -> parser/runtime | Source compiled; no user document/model supplied in device run |
| Camera/gallery | Permission and picker result -> bounded media gateway; invalid/absent model fails closed | Empty-state smoke; no clinical result claim |
| Search | Query state is owned by ViewModel and drives repository flow | Source/unit evidence; empty device state captured |
| Save/delete/retry | Sealed UI events, repository or gateway operation, visible success/error/unavailable state | Source/lint; full physical control journey is bounded by missing data |
| Pause/resume/load/unload | WorkManager/download manager and model repository typed state paths | Implementation/unit contract; no trusted manifest means physical download is `BLOCKED` |
| Accessibility | Meaningful content descriptions, semantic button roles, 48dp icon targets, visible labels/supporting text | Source/lint; full TalkBack and every target-size matrix pending |

## Runtime evidence gate

| Capability | Status | Reason |
| --- | --- | --- |
| LiteRT-LM chat | BLOCKED | `ModelRegistry.OFFICIAL_MODELS` is intentionally empty because no official URL, size, SHA-256, backend, and provenance tuple was verifiable in this checkout. No canned response is used. |
| Local vision | UNAVAILABLE | Image readability is validated, then analysis returns typed unavailable until a real vision model is initialized. No heuristic/benign/ABCD output is stored. |
| RAG indexing/search | BLOCKED | PDF/TXT/MD/DOCX parsing and provenance are real; no user SAF document and no verified local embedder were supplied. `EMBEDDER_UNAVAILABLE` is rendered. |
| Drug/lab reference catalog | UNAVAILABLE | No built-in synthetic catalog. Lab comparison requires the range from the actual report. |
| Clinical calculators | PASS (input/domain) | Blank, malformed, and out-of-range fields are rejected; no fabricated age/weight/height/date/lab/drug defaults. Reference-dependent calculations return unavailable. |

## Static, unit, and instrumentation evidence

| Gate | Result | Evidence |
| --- | --- | --- |
| `git diff --check` | PASS | Clean after documentation and source audit changes. |
| `./gradlew lintDebug --rerun-tasks` | PASS | Final run on 2026-08-19; lint report at `app/build/reports/lint-results-debug.html`. |
| `./gradlew testDebugUnitTest --rerun-tasks` | PASS | 44 test cases, 1 skipped, 0 failures. |
| `./gradlew assembleDebug --rerun-tasks` | PASS | Final debug APK assembled. |
| `./gradlew connectedDebugAndroidTest` | PASS (bounded) | Retry completed on Samsung `SM-G988B - 13`; 1 evidence-gate test, 0 failures. First attempt stopped at a transient Gradle cache move before device execution. |

## Physical device evidence

| Device | Result | Observed |
| --- | --- | --- |
| Samsung `RRCN3008VYE`, SM-G988B, API 33, 1440×3200 | PARTIAL PASS | Final APK installed with `adb install -r`; launch resumed `MainActivity`; Home, Chat, Skin, Knowledge Base, Tools, Model Manager, Persona empty/unavailable checkpoints have screenshots/UI dumps. Chat compact reflow shows detail, `No messages yet`, labelled `Clinical question`, and send control. Exact MedBot crash/ANR filter returned 0 matches. |
| Xiaomi `QSWSEMRKNFZ9LJRC`, API 35, 1220×2712 | BLOCKED | Serial was absent from `adb devices -l`; no physical claim is made. |

Evidence files:

- [live preview PNG](e2e/preview/medbot_live_preview-2026-08-19-samsung.png)
- [Chat blocked-state PNG](e2e/preview/medbot_chat_model_blocked-2026-08-19-samsung.png)
- [device UI dumps](e2e/device/)
- [JSON journey report](e2e/reports/medbot-e2e-2026-08-19.json)

The Samsung screenshot was captured with the final installed APK and shows
large text without the earlier Chat list-only failure. Physical validation of
all requested widths, orientation, TalkBack, keyboard/IME, airplane mode,
reminder permission, real model download, real RAG document, and real vision
photo remains `BLOCKED` or `UNAVAILABLE` until those real prerequisites exist.

## Delivery boundary

Only intentional MedBot files are eligible for staging. The nested
`docs/REFERENCES/GoldReference` and `docs/REFERENCES/reference3` repositories,
`.gradle/buildOutputCleanup/*`, and unrelated local reference changes remain
excluded. Before push, review the staged allowlist, run the secret scan, commit
on `main`, push without force, and compare local `HEAD` with remote
`refs/heads/main`.
