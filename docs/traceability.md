# MedBot verification traceability

Dokumen ini adalah matriks BMAD/spec-driven untuk `AGENTS.md`, `PRD.md`, `DESIGN.md`, `docs/MDFILE`, serta batas aman `docs/REFERENCES/GoldReference` dan `reference3`. Reference project dipakai sebagai pola pembelajaran; bukan sumber data production dan bukan bukti acceptance.

Status:

- `PASS`: bukti source/test/build tersedia pada batas yang ditulis.
- `FAIL`: source atau behavior bertentangan dengan requirement.
- `UNAVAILABLE`: feature fail-closed karena dependency nyata belum tersedia.
- `BLOCKED`: acceptance tidak dapat dilanjutkan tanpa device, model, dokumen, foto, permission, atau input nyata.

## BMAD/spec traceability

| Requirement / scope | Status | Evidence dan batas |
| --- | --- | --- |
| AGENTS: Android Kotlin + Compose + Material 3 | PASS | Source tree dan `assembleDebug` berhasil. |
| AGENTS: MVVM, UDF, StateFlow, lifecycle-aware Compose | PASS (source) | ViewModel owns UI state/events; Compose collects lifecycle-aware flows. Full runtime navigation tetap perlu device evidence. |
| AGENTS: Hilt composition root dan domain/data boundary | PASS (source/build) | Hilt module wires gateways; domain no longer imports concrete data implementations. |
| AGENTS: no `!!`, `runBlocking`, `GlobalScope`, main-thread blocking I/O | PASS (source/lint) | Production Kotlin audit bersih untuk pola tersebut; parser/downloader use bounded work and typed failure. |
| AGENTS: no fabricated data, fake output, placeholder feature, canned LLM | PASS | Canned LLM, heuristic skin baseline, synthetic clinical seeder, pseudo-pages, hash embedding fallback, dan fake model manifest dihapus/ditutup. Test fixtures hanya berada di test source. |
| AGENTS: explicit state/error/fail-closed behavior | PASS | Model/RAG/vision/input validators return typed unavailable/insufficient/error states. |
| AGENTS: permissions least privilege and privacy | PASS (source) | No `RECORD_AUDIO`, hidden phone permissions, raw identifiers, or cloud medical upload added; backup excludes sensitive local data. Device permission evidence pending. |
| AGENTS: Room migration explicit; no destructive migration | PASS | Database version 2 has explicit `MIGRATION_1_2`; no destructive fallback. Migration test coverage remains component evidence. |
| PRD: Indonesian + English only | PASS | `values/` and `values-en/`; Hindi/auth are intentionally out of scope. |
| PRD: local LiteRT-LM runtime | BLOCKED | Real adapter is wired and validates `.litertlm`, size, digest, URI/path, backend, and initialization; no verified model bundle is available. |
| PRD: model manager/download | UNAVAILABLE | Downloader supports HTTPS, resume, range validation, timeout, storage check, measured speed, SHA-256, and atomic rename; trusted registry is empty, so no download acceptance can start. |
| PRD: triage and 46-agent registry | PASS (unit) | Registry and red-flag tests execute; model-dependent chat remains blocked. |
| PRD: real local RAG | BLOCKED | TXT/MD, PdfBox PDF, and OpenXML DOCX parsing preserve original SHA-256, page/section provenance, and no pseudo-pages; real user document and local embedder are unavailable. |
| PRD: skin scan and lineage | UNAVAILABLE | Image readability is checked; without an initialized local vision model, result is `UNAVAILABLE`/`INSUFFICIENT_DATA` and no record is fabricated. |
| PRD: clinical tools | PASS (input/domain) | Deterministic calculator validation rejects blank/malformed/out-of-range fields; drug/lab catalog is `UNAVAILABLE` until verified source data is supplied. |
| DESIGN: responsive/adaptive UI | PARTIAL PASS | Material tokens, adaptive widths, wrapping rows, touch targets, semantics, loading/error states, and microinteraction wiring are present. Samsung 1440×3200 captures show no black void or crash; 320/360/411/600/840+ dp and large-font physical matrix remains unverified. |
| DESIGN: navigation and interactive controls | PARTIAL PASS | Screen event handlers and back/navigation paths exist; Samsung bottom-navigation smoke and screen dumps pass. Full control-by-control journey remains bounded by missing real model/document/photo inputs. |
| DESIGN: no user-facing hardcoded strings | PASS (source target) | New/modified surfaces use localized resources; legacy surface audit and full locale visual pass remain device/doc review items. |
| MDFILE: use references for architecture, not fabricated evidence | PASS | GoldReference/reference3 boundaries were treated as patterns; no auth, Hindi, cloud AI, offensive capability, or reference data was copied into production. |
| GoldReference safe boundary | PASS | Local-only direction retained; model installation is evidence-gated and no cloud fallback was added. |
| User: RTK/CAVEMAN/PONYTAIL | PASS (authoring) | RTK used for Gradle command output where available; CAVEMAN/PONYTAIL are guidance only and not runtime dependencies. |

## Screen / feature / control matrix

| Screen | Controls and navigation | Acceptance status |
| --- | --- | --- |
| Home | Bottom navigation, quick actions to chat/skin/tools/knowledge/models/persona, refresh/recommendation state | PASS (source + Samsung smoke); screenshot/UI dump in `docs/e2e/preview/medbot_live_preview-2026-08-19-samsung.png` and `docs/e2e/device/medbot_home-2026-08-19-samsung.xml` |
| Chat | New session, agent/persona selector, prompt chips, attachment picker, send, retry/error, citation dialog, delete session/message, back | PASS (screen/state smoke); real inference BLOCKED without `.litertlm`, checkpoint in `medbot_chat_model_blocked-2026-08-19-samsung.png` |
| Skin Scan | Camera/gallery picker, image preview, body-part/notes, analyze, delete/back | PASS (empty-state smoke); vision `UNAVAILABLE` without a real local model |
| Skin Lineage | Timeline list, record detail, before/after interaction, delete/back | PASS (empty-state smoke); no real photo/record supplied |
| Knowledge Base | SAF OpenDocument, parse, ingest, search, delete, error/empty states | PASS (empty-state smoke); real document + embedder BLOCKED |
| Model Manager | Open `.litertlm`, load, unload, pause/resume/cancel download, retry/error | PASS (unavailable-state smoke); registry/model unavailable |
| Persona | Language, tone, agent, depth, save/back | PASS (screen smoke); checkpoint in `medbot_persona-2026-08-19-samsung.png` |
| Medical Tools | Pediatric/BMI/due-date/lab/interaction/reminder controls, validation, result/error/unavailable states | PASS (fresh empty/unavailable smoke); calculator validation PASS; catalog-dependent tools unavailable |

## Test and acceptance ledger

| Gate | Result | Evidence |
| --- | --- | --- |
| `git diff --check` | PASS | Clean on the staged diff and verified again before delivery on 2026-08-19. |
| `:app:lintDebug --rerun-tasks` | PASS | Completed 2026-08-19; lint report generated at `app/build/reports/lint-results-debug.html`. |
| `:app:testDebugUnitTest --rerun-tasks` | PASS | 44 tests completed, 1 skipped, 0 failures on 2026-08-19. |
| `:app:assembleDebug --rerun-tasks` | PASS | Debug APK assembled on 2026-08-19. |
| `:app:connectedDebugAndroidTest` | PASS (bounded) | One `EvidenceGateComposeTest` passed on `SM-G988B - 13` (API 33); result XML is in `app/build/outputs/androidTest-results/connected/debug/`. This is not real model/RAG/vision acceptance. |
| Real model load/chat | BLOCKED | No verified `.litertlm` file. |
| Real SAF/RAG | BLOCKED | No user-selected document and no local embedder runtime. |
| Real camera/gallery skin analysis | UNAVAILABLE | No local vision model; no clinical output permitted. |
| Device launch/navigation/logcat | PARTIAL PASS | Samsung `RRCN3008VYE`, SM-G988B, API 33, 1440×3200: launch, bottom navigation, empty/unavailable screen dumps and bounded crash/ANR scan passed. Xiaomi `QSWSEMRKNFZ9LJRC`, API 35, was absent from `adb devices -l` and is BLOCKED. Full report: `docs/e2e/reports/medbot-e2e-2026-08-19.json`. |

## Delivery boundary

Only intentional MedBot files may be staged. Nested `docs/REFERENCES/GoldReference` and `docs/REFERENCES/reference3` are separate dirty repositories and remain excluded. `.gradle/buildOutputCleanup/*` is generated state and remains excluded. Before push: inspect staged diff, run secret scan, commit on `main`, push, and compare local `HEAD` with `refs/heads/main`.

The initial `install -r` smoke retained an older local database containing catalog rows; those screenshots were discarded. The connected test uninstall and final reinstall produced the fresh evidence used here, with no `pm clear` or destructive data operation performed.
