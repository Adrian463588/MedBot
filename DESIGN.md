# MedBot Design Contract — Anti AI Slop

Status: active UI/UX and architecture contract
Scope: Kotlin, Jetpack Compose, Material 3, local-first clinical information support

This document is the UI contract for MedBot. It is derived from `docs/AntiSlop/Reference1.md` and `docs/AntiSlop/Reference2.md`, then constrained by `AGENTS.md`, `PRD.md`, and the evidence boundary of this repository. A visual feature is not complete merely because it renders: it must have a real event path, a truthful state, an accessible target, and a responsive layout.

## 1. Product posture

MedBot is a calm, local-first clinical information companion. It is not a doctor, diagnostic device, prescription service, emergency service, or cloud AI wrapper.

The UI must never hide a missing capability behind a success state. Production code must not contain canned responses, synthetic clinical documents, fabricated model metadata, fake citations, heuristic skin diagnoses, default clinical inputs, emoji decoration, placeholder controls, or decorative AI theatre.

The only accepted production AI path is:

```text
user action
  -> ViewModel event
  -> domain use case
  -> repository/gateway
  -> validated local asset/runtime
  -> typed result
  -> StateFlow
  -> stateless Compose UI
```

Missing evidence is rendered as `UNAVAILABLE`, `INSUFFICIENT_DATA`, `MODEL_UNAVAILABLE`, or `EMBEDDER_UNAVAILABLE`. It is never converted into a fabricated answer.

## 2. AntiSlop principles

### Content first

Each screen has one dominant purpose and one primary action:

| Screen | Dominant purpose | Primary action |
| --- | --- | --- |
| Home | Understand local readiness | Start consultation |
| Chat | Read and compose a consultation | Send a labelled question |
| Skin Scan | Provide a real photo and metadata | Import/capture, then request analysis |
| Skin Lineage | Review stored photo records | Select a record |
| Knowledge Base | Add user-owned source material | Import a document through SAF |
| Model Manager | Validate and load a local model | Choose a `.litertlm` file |
| Persona | Configure response context | Save |
| Medical Tools | Run a selected input-driven tool | Submit explicit inputs |

Secondary actions remain visually subordinate. Quick access is a short list, not a feature grid.

### Things intentionally excluded

- no decorative gradients, animated backgrounds, glassmorphism, floating blobs, or constant motion;
- no nested cards, card-soup, dashboard tile grids, fake avatars, stock illustrations, or emoji as UI;
- no `TextField.placeholder`; use label, supporting text, validation text, and an informative empty state;
- no disabled button that looks unexplained: disabled state must have visible supporting context;
- no numerical metric without a real source, timestamp, unit, and provenance;
- no copy that says “AI ready” unless a validated local runtime is initialized.

## 3. Material 3 Expressive foundation

The implementation uses Material 3 semantic roles rather than screen-specific colors. Expressive character comes from hierarchy, shape, typography, selected-state contrast, and purposeful motion—not decoration.

### Tokens

- spacing: `4 / 8 / 16 / 24 / 32 / 48dp`;
- touch target: at least `48 × 48dp` for every interactive control;
- radii: small `8dp`, medium `12dp`, large `20dp`; use the theme shape scale;
- elevation: tonal surface and borders first; use minimal shadow elevation;
- colors: `primary`, `onPrimary`, `primaryContainer`, `secondaryContainer`, `surface`, `surfaceVariant`, `outlineVariant`, `error`, and explicit urgency roles;
- typography: `displaySmall`/`headline` for page intent, `title` for sections, `body` for evidence, `label` for metadata and controls;
- icons: Material icons with content descriptions on meaningful controls; decorative icons use `null` only when adjacent text already conveys the same meaning.

### Urgency and evidence roles

Urgency colors are semantic and are never used to imply a diagnosis:

- emergency: red;
- high: orange;
- medium: yellow/amber;
- low: green only when a real policy result supports it;
- insufficient data: slate/neutral.

The `INSUFFICIENT_DATA` role is a first-class state, not an error hidden in a snackbar.

## 4. Adaptive layout contract

The root navigation owns the window insets. Child screens render content and do not create a second root `Scaffold`.

| Width | Navigation | Content strategy |
| ---: | --- | --- |
| `< 600dp` | `NavigationBar` | One-column content-first flow |
| `600–839dp` | `NavigationRail` | Constrained content with reflowed actions |
| `>= 840dp` | `NavigationRail` | `ListDetailPaneScaffold` for list/detail workflows |

The layout must be checked at 320, 360, 411, 600, and 840+ dp, in portrait and landscape, with normal and large font scale. A wide phone is not treated as a stretched phone layout.

### Multi-pane screens

Material Adaptive `ListDetailPaneScaffold` is used for:

- Chat: session list and message detail;
- Knowledge Base: document/search list and provenance detail;
- Skin Lineage: record list and selected record detail.

On compact windows the scaffold reflows to a single visible pane. Back navigation returns from detail to list before leaving the route. Selected keys are stable entity IDs, never list positions.

### Edge-to-edge

`MainActivity` calls `enableEdgeToEdge()`. The root `Scaffold` uses `ScaffoldDefaults.contentWindowInsets`. The top bar consumes only horizontal safe-drawing insets because the root owns vertical system-bar insets. Content uses the root padding and does not add manual zero-inset voids. Keyboard, cutout, gesture navigation, API 35, rotation, and landscape are acceptance cases.

Material 3 is pinned to `1.4.0`. The current toolchain is AGP `8.8.2`/compile SDK `35`, so the project uses stable Material Adaptive `1.2.0` through the stable `ListDetailPaneScaffold` API. Adaptive `1.3.0` remains a compatibility decision to verify separately; alpha APIs are not introduced as a silent workaround.

## 5. Motion and interaction

Motion exists only to communicate feedback, state change, continuity, or hierarchy:

- pressed controls use a short scale response connected to the actual pointer interaction source;
- Material ripple communicates press;
- haptic feedback is limited to meaningful actions and is not required for task completion;
- loading, success, error, retry, cancellation, and disabled states are visible;
- route and pane transitions are short and content-preserving;
- no infinite animation or attention-seeking decoration;
- reduced-motion users receive state changes without decorative animation.

Every button, chip, tab, icon button, card-like list row, slider, picker, delete, save, retry, pause, resume, load, unload, search, and back action has a real handler or an explicit disabled reason.

## 6. Screen contracts

### Home

Shows only measured local readiness: model loaded state, active model path when known, and document count from Room. It has one primary “Start consultation” action and secondary links to Model Manager, Knowledge Base, Skin, Tools, and Persona.

### Chat

The message stream is the visual focus. The composer has a persistent label and supporting safety text. Attachments are real user-selected files only. A photo is not sent into text-only LiteRT-LM; it produces `VISION_UNAVAILABLE` until a vision-capable local runtime is implemented. The composer contains one compact bento control for optional web evidence: it is off by default, names the privacy boundary, and makes clear that the local model still generates the answer. Local SAF/BankBook retrieval runs first; the network gateway is called only when local evidence is insufficient and the user has opted in. It sends only a sanitized current topic, uses fixed HTTPS official-source allowlists and robots policy, and never sends history, profile, or images. Web citations show source role, provenance, and a 48dp action to open the verified HTTPS source. Network failure remains an explicit unavailable state.

Each active chat session keeps one LiteRT-LM `Conversation`, so subsequent turns reuse the native KV cache instead of creating a new conversation. The system instruction is prefetched once; per-turn RAG evidence stays in the user content, and the conversation is rebuilt from bounded Room history before the measured KV token count reaches the model budget. KV state is intentionally in-memory and is not persisted as raw model tensors. While local search, web evidence retrieval, or inference is running, the response row exposes a truthful phase and a 48dp cancellation action; cancellation never saves a partial assistant answer.

### Skin Scan and Skin Lineage

Camera/gallery input is real and copied through a bounded app-private media gateway. The body location is explicit; no body location is silently selected. Invalid files, failed permission, missing metadata, and absent vision runtime produce `INSUFFICIENT_DATA` or `UNAVAILABLE`. No benign baseline, ABCD score, differential diagnosis, or urgency is persisted without a real validated vision model. The chat path accepts an image only through LiteRT-LM `Content.ImageFile` after a vision-capable engine has initialized; a text-only runtime cannot claim vision support.

### Knowledge Base

Documents enter through SAF or the release-owned BankBook JSONL asset. PDF uses a real PDF parser; TXT/MD, DOCX, and JSONL retain unknown page numbering when the format has no authoritative pages. SHA-256 is computed from the original bytes and the BankBook asset is checked against its pinned checksum before Room promotion. Chunks retain document ID, section, page when authoritative, and provenance. Embedding failure is `EMBEDDER_UNAVAILABLE`; no hash-based pseudo-vector is allowed.

### Model Manager

The screen separates two SAF actions: choose a writable model destination folder with `ACTION_OPEN_DOCUMENT_TREE`, or import one `.litertlm` file with `ACTION_OPEN_DOCUMENT`. A downloaded model is loaded only after the SAF artifact is re-read and its manifest filename, exact size, SHA-256, backend, capability, and LiteRT-LM initialization succeed. The release-owned registry exposes six manifests, including the HAI-DEF-gated MedGemma 1.5 4B IT Vision artifact. Its official size and SHA-256 are pinned, but the no-auth app does not bypass Hugging Face access; the user opens the official source, accepts its terms, confirms that acceptance in the app, and provides a read-only token. Download supports bounded resume, HTTP status and `Content-Range` validation, ETag protection, measured speed, SHA-256, and SAF promotion. An integrity failure discards only the failed `.part` and its ETag sidecar so Retry starts from byte 0; an invalid canonical final is quarantined as `.rejected-*` instead of blocking the next candidate. Startup and reinstall recovery first validate the stored tree URI against an active persisted write grant and writable directory. A stale preference never counts as a usable destination; the app scans persisted SAF tree grants for verified model or partial artifact names, restores a matching tree URI, and only then enables transfer actions. It asks for re-authorization if Android revokes the grant; the external artifact is not treated as app-private data.

Gated access uses progressive disclosure: the token field and terms confirmation are shown only while that exact source revision still needs setup. After the encrypted token and source-term acceptance are present, the card shows a compact readiness state; an unauthorized response reopens only the token field, and clearing the token clears the associated acceptance. The bearer token is never written to SAF in plaintext or included in WorkManager input; an encrypted credential envelope, source revision, and non-secret verification metadata are stored beside the model files when a writable SAF folder is available. For gated MedGemma, the worker resolves the official LFS SHA through the authenticated repository tree API before downloading and writes that resolved contract to SAF; a masked or unavailable SHA fails closed. The registry's bootstrap metadata is never used as a substitute for this authenticated gated-artifact contract. Rebuild/update installation can restore the envelope while the same Keystore key and SAF grant remain available. A full uninstall may remove the Keystore key or SAF grant, so the encrypted backup remains fail-closed and secure re-entry may still be required; the app never guesses or decrypts it with a hardcoded key. The model file, `.part` file, and non-secret ETag resume sidecar remain in the user-selected SAF folder. If the SAF grant is revoked after uninstall, the user re-authorizes the folder before reconciliation; the external model is not reported as lost.

The transfer action row is status-driven for both gated and public manifests: `Download`, `Pause`, `Resume`, `Cancel`, and `Retry` are rendered from the actual WorkManager/SAF state, never from the access type alone. A gated access requirement only disables a download/resume/retry action until the token, terms revision, and writable SAF destination are ready.

While a transfer is actually running, the Worker is promoted to a `dataSync` foreground worker and exposes one quiet, ongoing system notification per model. The notification shows the verified model display name, real transferred bytes, measured speed, and verification state; tapping it opens Model Manager. Android 13+ notification permission is requested at the user-initiated Download/Resume action. Denial never fabricates progress and does not change the download's SAF integrity rules.

LiteRT-LM receives a writable `EngineConfig.cacheDir` at `context.cacheDir/litertlm-models`. This is a rebuildable engine/weight cache for faster subsequent initialization, not the model source of truth and not the conversation KV cache. The directory is created and validated before `Engine.initialize()`; Android may evict it, so every load path must tolerate a cold cache and rebuild from the verified SAF model.

### Persona

Language is limited to Bahasa Indonesia and English. Tone, depth, agent, patient profile, and custom instructions use explicit labels and are stored through the ViewModel/DataStore path. Auth and Hindi are outside the product boundary.

### Medical Tools

Inputs have no fabricated defaults. BMI and date arithmetic use only explicit values and are presented as calculations, not diagnoses. Pediatric Z-score and dosing remain unavailable without an official growth dataset and verified medication monographs. Lab comparison requires the reference range supplied by the actual report; hardcoded generic ranges are not used. Reminders persist only the time/title entered by the user.

## 7. Architecture contract

```text
presentation/  Compose screens, immutable UI state, events, ViewModels
domain/        pure policies, models, use cases, repository interfaces
data/          Room/DataStore implementations, parsers, runtime adapters
platform/      Android-specific permissions, SAF, media, and system gateways
core/          Hilt graph and design system
```

Rules:

- Compose renders state and emits events; it does not call DAOs, WorkManager, LiteRT-LM, or raw Android services directly;
- ViewModels use `viewModelScope`, `StateFlow`, cancellation, and lifecycle-aware collection; they do not hold an Activity or View;
- domain does not import data implementations;
- data maps framework errors into typed domain failures;
- Room migrations are explicit and destructive migration is not enabled;
- no `runBlocking`, `GlobalScope`, `!!`, main-thread file/database work, empty catches, or silent infinite retry;
- duplicated permission, validation, mapping, and navigation behavior belongs in one owner.

## 8. State and evidence contract

Every capability exposes an honest state. Typical states are `IDLE`, `RUNNING`, `AVAILABLE`, `UNAVAILABLE`, `INSUFFICIENT_DATA`, `PERMISSION_REQUIRED`, `MODEL_UNAVAILABLE`, `EMBEDDER_UNAVAILABLE`, `FAILED`, `STOPPED`, and `CANCELLED`.

Test fixtures may exist in `src/test` or `src/androidTest`, but they do not seed production Room, ship in the APK, or become runtime success. Physical acceptance records device/API/permission/input/model provenance separately from static/build evidence.

## 9. Verification checklist

- [ ] UI text has Bahasa Indonesia and English resources.
- [ ] No placeholder, mock, dummy, synthetic, canned, or fabricated production output.
- [ ] All interactive controls have a handler, state, and accessible label.
- [ ] Every target is at least 48dp and usable with large text.
- [ ] Empty/loading/error/unavailable/permission states are visible.
- [ ] Compact, medium, expanded, landscape, keyboard, and edge-to-edge layouts are checked.
- [ ] Local model/RAG/vision results are blocked without real validated evidence.
- [ ] `lintDebug`, unit tests, assembly, and connected tests are reported separately from physical-device acceptance.
