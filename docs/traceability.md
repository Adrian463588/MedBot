# MedBot verification traceability

This matrix records the verification/documentation slice only. It does not convert source inspection into device, model, or clinical acceptance.

Status meanings: `PASS` = verified in the stated slice; `FAIL` = current source conflicts with the constraint; `UNAVAILABLE` = required evidence was not produced; `BLOCKED` = a prerequisite prevents the gate from proceeding.

| Source constraint | Status | Evidence and boundary |
| --- | --- | --- |
| `AGENTS.md`: keep model/device claims separate from unit and static evidence | PASS | README, Compose gate, and journey spec explicitly separate these gates. |
| `AGENTS.md`: no fabricated model, document, photo, or device output | PASS | New assets accept only explicit real inputs; missing inputs render `BLOCKED`. Existing stale preview is not linked as fresh evidence. |
| `AGENTS.md`: production changes stay minimal and scoped | PASS | This slice is limited to `app/src/test`, `app/src/androidTest`, `.github`, `README.md`, `docs/traceability.md`, and `docs/e2e/journeys`. |
| `AGENTS.md`: explicit state/error handling | PASS | Tests cover `ModelDownloadStatus`, `DownloadProgress`, empty embeddings, incompatible vectors, and unknown-tool failure. |
| `AGENTS.md`: missing model/photo must fail closed | FAIL | `LlmInferenceEngine.loadModel` currently marks a missing path loaded, and `SkinDiagnosisEngine` returns a baseline record for a missing image. Production remediation is out of scope here. |
| `AGENTS.md`: no swallowed or simulated clinical success | FAIL | Existing production fallback behavior remains; the model-dependent use-case test is ignored rather than treated as model evidence. |
| `PRD.md`: Android API 26–35, Compose, offline-first intent | UNAVAILABLE | Configuration is present, but the requested local build did not reach a passing compile gate. |
| `PRD.md`: dual-mode model acquisition and checksum integrity | FAIL | Registry metadata is structurally tested only; no model is verified, and current worker code does not provide a completed SHA-256 acceptance result. |
| `PRD.md`: local RAG parser/chunker pipeline | UNAVAILABLE | Parser/chunker tests and fixtures were added; execution is blocked by `:app:kspDebugKotlin`. No real document ingestion is claimed. |
| `PRD.md`: triage and 46-agent registry | UNAVAILABLE | Registry/triage contracts are covered by tests; no executed test result is available because the build is blocked. |
| `PRD.md`: skin lineage with camera/photo and vision model | BLOCKED | No real photo, model, device, or fresh runtime observation is available. |
| `PRD.md`: airplane-mode clinical acceptance | BLOCKED | No physical device run in airplane mode was performed. |
| `DESIGN.md`: Clean Architecture/MVVM/UDF and testability | UNAVAILABLE | Pure contract coverage is added, but build execution is blocked; no architecture claim is inferred from the test-only Compose shell. |
| `DESIGN.md`: RAG metadata, chunk overlap, and local vector boundary | PASS | Test fixtures verify parser sanitization, section metadata, overlap, empty-page behavior, zero-vector behavior, and dimension mismatch handling. This is component evidence only. |
| `DESIGN.md`: real model/photo evidence gate | PASS | `EvidenceGateComposeTest` and `medbot_evidence_gate.xml` stop at `BLOCKED` without explicit readable inputs. |
| `docs/MDFILE/reference1.md`: local on-device LLM/RAG patterns are guidance, not proof | PASS | No reference implementation was copied or used as runtime evidence. |
| `docs/MDFILE/reference2.md`: tests should cover domain/data logic and instrumented UI separately | PASS | Unit tests remain in `app/src/test`; Compose evidence test remains in `app/src/androidTest`; physical execution is not claimed. |
| `docs/MDFILE/reference3.md`: clinical decision support requires independent clinical verification and evidence abstention | PASS | README states decision-support limits; journey blocks when evidence is absent. |
| `docs/MDFILE/reference3.md`: local inference after model installation | BLOCKED | No model installation or inference run is available. |
| Nested reference repositories: read-only learning material | PASS | No nested reference path was edited; pre-existing nested worktree dirtiness remains untouched. |
| User request: pinned JDK GitHub Actions for lint/unit/assemble | PASS | `.github/workflows/android-verification.yml` uses Temurin JDK 17 with `check-latest: false` and runs all three requested gates. |
| User request: no placeholder repository URL or stale screenshot claim | PASS | README has no clone URL and marks physical preview unavailable; future path is documented without creating an artifact. |
| User request: read a standalone plan | UNAVAILABLE | No standalone plan file was found outside the governing `AGENTS.md`, `PRD.md`, and `DESIGN.md`; this matrix follows the explicit user scope. |

## Validation record

Observed local command:

```text
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Result: `BLOCKED` at `:app:kspDebugKotlin` with `unexpected jvm signature V`. A prior offline attempt was blocked because `kotlin-gradle-plugin:2.2.21` was not cached. No test pass, APK, instrumentation run, model load, document ingestion, photo analysis, or physical screenshot is claimed.
