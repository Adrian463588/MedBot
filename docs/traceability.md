# MedBot Chatbuddy verification traceability

Matriks ini adalah catatan BMAD/spec-driven untuk `AGENTS.md`, `PRD.md`,
`DESIGN.md`, `docs/MDFILE`, dan referensi AntiSlop. `GoldReference` serta
`reference3` hanya dipakai sebagai referensi pola; keduanya bukan source
production dan tidak boleh distage.

Status yang digunakan:

- `PASS`: kontrak terbukti oleh source atau test yang relevan.
- `FAIL`: kontrak diuji dan gagal.
- `PARTIAL_PASS`: sebagian jalur terbukti, acceptance lanjut masih tertunda.
- `UNAVAILABLE`: capability nyata belum tersedia sehingga aplikasi fail-closed.
- `BLOCKED`: acceptance membutuhkan model, permission, input, device, atau
  kondisi eksternal yang belum tersedia.

## CB-001 sampai CB-010

| ID | Requirement | Status | Evidence / batas aman |
| --- | --- | --- | --- |
| CB-001 | Local LiteRT-LM inference | PARTIAL_PASS (source/build); BLOCKED physical answer | `LlmInferenceEngine` hanya memakai LiteRT-LM nyata, lifecycle `Conversation` per sesi, streaming delta, cancellation, context budget, dan file cache. Tidak ada canned/fallback answer. Physical run belum memiliki model `.litertlm` tervalidasi yang loaded, sehingga jawaban klinis tidak diklaim. |
| CB-002 | Bundled/uploaded local RAG | PASS (source/unit/instrumentation) | BankBook asset `2.2.0` diverifikasi checksum, di-embed dengan MiniLM TFLite nyata, diimpor atomik ke Room, dan query diare terbukti pada Samsung memakai Room + embedder + corpus. Dokumen SAF mempertahankan checksum/provenance. |
| CB-003 | Clinical medication evidence | PASS retrieval gate; BLOCKED generated answer | PPK/ISO monograph bertipe `GUIDELINE`/`DRUG_MONOGRAPH` dapat memenuhi gate; product catalogue, classification index, dan secondary education tidak bisa. Instrumentation membuktikan query obat mengembalikan evidence `medicationEligible`; model belum loaded untuk menghasilkan jawaban. |
| CB-004 | Triage dan dynamic probing | PASS (source/unit) | `TriageOrchestrator` mengeluarkan urgency/red flags; `ClinicalResponsePlanner` meminta usia, durasi, gejala terkait, kehamilan, alergi, obat berjalan, komorbid, dan tanda bahaya tanpa nilai default. |
| CB-005 | Guardrail clinical output | PASS (source/unit) | Citation ID harus terikat ke evidence yang ditampilkan; dosis/angka yang tidak ada di evidence ditolak; resep individual, diagnosis final, dan racikan tanpa protokol eksplisit ditolak. |
| CB-006 | Web fallback evidence-only | PASS (source/unit); BLOCKED physical network journey | Local RAG selalu dicoba lebih dahulu. Web default-off, opt-in, query saat ini disanitasi, allowlist hanya HTTPS WHO/NCBI/PubMed, robots/timeout/size/rate boundary diterapkan, dan LiteRT-LM lokal tetap generator. Tidak ada arbitrary scraping Halodoc/K24 di runtime. |
| CB-007 | Citation click-through | PASS source/unit; PARTIAL physical | Citation web menyimpan URL/hash/freshness dan hanya membuka host allowlist; citation SAF menyimpan URI serta read grant; revoked grant menghasilkan state permission. UI dialog dan serialization diuji, physical click-through belum dijalankan. |
| CB-008 | Loading/error/cancel/unavailable state | PASS source/unit/instrumentation | Chat memiliki satu `ChatUiState`, phase retrieval/indexing/generating, retry/cancel, error code, no partial save after cancellation. Notification instrumentation memakai byte progress nyata. |
| CB-009 | Clean responsive Chatbuddy UI | PASS source; PARTIAL physical | Material 3 AntiSlop, content-first hierarchy, adaptive navigation, edge-to-edge, IME-aware composer, 48dp controls, semantic labels, and citation chips are implemented. Physical width/font/orientation matrix lengkap belum dijalankan. |
| CB-010 | End-to-end physical acceptance | PARTIAL_PASS | Samsung launch, 7/7 instrumentation, real BankBook RAG, install/reinstall preservation, and crash/ANR scan pass. Full model generation, web fallback, SAF model download/recovery, vision inference, and Xiaomi API 35 remain blocked/unavailable. |

## Architecture and data boundary

The production path is:

```text
ChatViewModel
  -> SendMessageUseCase
  -> triage + probing
  -> typed local evidence/RAG
  -> optional allowlisted web evidence
  -> LiteRT-LM local inference
  -> citation/structure guardrail
  -> Room ChatRepository
```

- The application is Kotlin, Compose, Material 3, Hilt, Room, MVVM/UDF, and
  lifecycle-aware `StateFlow`.
- `ClinicalEvidenceRepository` separates guideline, drug monograph,
  interaction, compounding protocol, textbook reference, product catalogue,
  classification-only, and secondary education material.
- `BankBookCorpusManifest` is pinned to 1,132 records, `2,642,327` bytes,
  corpus SHA-256 `08dc04293e6e4b36e811b64cd3a0ac165962ea484d16799d97e530a4410b629a`,
  and embedding asset SHA-256
  `5c5b897c436126bda7814f24676e021b50302e46c7f5c99e85f4e1c0341bf95e`.
- Room is schema version 7 with explicit non-destructive migrations `1→2`,
  `2→3`, `3→4`, `4→5`, `5→6`, and `6→7`. Version 5→6 adds source/citation
  provenance; 6→7 adds embedding version.
- The six release-owned model manifests are official HTTPS artifacts with
  exact size, revision, provenance, and capability. MedGemma remains HAI-DEF
  gated; the app does not bypass source terms or invent a checksum.
- RTK, CAVEMAN, and PONYTAIL are authoring guidance only, not runtime
  dependencies.

## Model, RAG, and web acceptance

| Capability | Status | Evidence / boundary |
| --- | --- | --- |
| BankBook seed and retrieval | PASS | Real `MiniLM TFLite + Room + BundledKnowledgeSeeder` instrumentation passed diare triage and medication retrieval with citation IDs and 64-character source SHA. |
| Parser and provenance | PASS | JSONL nested sections, record ID, source role, source URL, revision, source SHA, evidence kind, page/section, and SAF URI are preserved; no pseudo-page or `hashCode()` SHA. |
| Atomic reindex | PASS (source/unit compile) | Candidate embedding and parsing complete before `RagDao.replaceDocumentWithChunks`; old index is preserved if candidate preparation fails. |
| Medication recommendation | UNAVAILABLE/BLOCKED when source/model absent | Only eligible monograph/guideline/interaction/protocol evidence can enter the gate. The product catalogue alone is insufficient; LiteRT-LM generation still requires a loaded model. |
| Web fallback | PASS source/unit; BLOCKED physical | WHO/NCBI/PubMed evidence is bounded and citation-bearing. No history, profile, image, or identity is sent. Results are in-memory TTL evidence and are not silently persisted as permanent RAG. |
| Vision | UNAVAILABLE | No text-only fallback, heuristic skin diagnosis, benign baseline, or fabricated visual result is allowed. |

## Static, unit, instrumentation, and build evidence

| Gate | Result | Evidence |
| --- | --- | --- |
| `git diff --check` | PASS | No whitespace errors; Windows LF/CRLF warnings are checkout normalization only. |
| `.\gradlew.bat :app:lintDebug --rerun-tasks` | PASS | No MedBot lint errors; only the upstream duplicate TensorFlow Lite support namespace warning remains. |
| `.\gradlew.bat :app:testDebugUnitTest --rerun-tasks` | PASS | 112 tests, 0 failures, 0 errors, 0 skipped. |
| `.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --rerun-tasks` | PASS | Current debug APK: 185,154,036 bytes, SHA-256 `0a287d1ea1c883ce5c745d27322ff3c15e8767e80b266fc035fbac2cfdf6bd83`. |
| `.\gradlew.bat :app:connectedDebugAndroidTest --rerun-tasks` | PASS (Samsung) | 7 tests, 0 failures, 0 errors, 0 skipped on SM-G988B API 33. |
| Production source scan | PASS | Kotlin/XML/Gradle production source has no forbidden canned/mock/dummy/fake/synthetic/placeholder/runBlocking/`!!`/`saf://` implementation. Binary model vocabulary and clinical source bytes are excluded from keyword scanning. |

## Physical-device evidence

| Device | Result | Observed |
| --- | --- | --- |
| Samsung `RRCN3008VYE`, SM-G988B, API 33, 1440×3200 | PARTIAL_PASS | APK reinstalled with `adb install -r -d` without clearing app data. Package was re-enabled for user 0 after instrumentation left it disabled. Launcher resolved, `com.medbot.app/.MainActivity` resumed, and bounded logcat contained no `FATAL EXCEPTION` or `ANR`. Real BankBook RAG instrumentation passed 7/7, including medication-eligible retrieval. No loaded model means no generated clinical answer claim. |
| Xiaomi `QSWSEMRKNFZ9LJRC`, API 35, 1220×2712 | BLOCKED | Device is visible, but Android policy returns `INSTALL_FAILED_USER_RESTRICTED`; no Xiaomi UI or inference claim is made. |

Current preview and machine-readable evidence:

- [Current Home physical preview](e2e/preview/medbot_home_chatbuddy-2026-08-27-samsung.png)
- [Chatbuddy device report](e2e/reports/medbot-chatbuddy-2026-08-27.json)
- [Historical model manager preview](e2e/preview/medbot_model_loaded_final-2026-08-19-samsung.png) — model-specific evidence only.

Not yet physically verified: full model download/resume/checksum/reinstall recovery
on this run, loaded LiteRT-LM response, MedGemma gated artifact, vision
inference, web fallback, SAF citation opening, permission revoke/re-authorize,
airplane mode, camera photo, full 320/360/411/600/840+ dp matrix, landscape,
large font/TalkBack audit, and Xiaomi API 35. These remain `BLOCKED` or
`UNAVAILABLE`; no fabricated pass claim is made.

## Delivery boundary

Only intentional MedBot files are eligible for staging. Do not stage nested
`docs/REFERENCES/GoldReference`, `docs/REFERENCES/reference3`,
`.gradle/buildOutputCleanup/*`, raw scraped caches, raw clinical books, tokens,
secrets, APKs, model staging, or device files. Review the staged allowlist and
remote `main` SHA before delivery.
