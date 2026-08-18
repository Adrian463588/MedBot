Recommended Architecture for an Offline Medical LLM Android App — August 2026

For your target—Jetpack Compose, fully local inference after model download, user-uploaded RAG documents through SAF, resumable model downloads, configurable personas, and medical image input—I would build the application around LiteRT-LM + Gemma 4 + a custom on-device RAG layer, rather than starting a new project on MediaPipe LLM Inference.

Google now describes MediaPipe LLM Inference as maintenance-only and recommends LiteRT-LM Kotlin for Android instead. LiteRT-LM supports Android, GPU/NPU acceleration, multimodality, tool use, and current model families including Gemma. (Google AI for Developers)

There is one important medical-design boundary: I would build this as a clinical decision-support system, not as an autonomous doctor. Even Google's medical-specific MedGemma documentation says its output is preliminary and is not intended to directly determine diagnosis, treatment, or patient-management decisions without appropriate validation and independent clinical verification. (Google for Developers)

1. Recommended model strategy

I would not use a single model for everything.

Use a three-layer model architecture:

LayerRecommended modelPurposeMain LLMGemma 4 E2B ITDefault chatbot for POCO X7 Pro 8 GBHigher-quality LLMGemma 4 E4B ITOptional model for POCO X7 Pro 12 GBMedical specialistMedGemma 1.5 4B ITExperimental/validated medical text + medical-image specialistEmbedding modelGecko 110M quantizedRAG embeddingsMedical vision specialistMedSigLIP / validated custom LiteRT modelSkin/wound/image classificationSafety modelssmall LiteRT classifiers/rule enginesRed flags, contraindications, toxicity, prescription safety

Why Gemma 4 E2B/E4B?

Gemma 4 E2B and E4B are explicitly targeted at mobile/edge devices and support text, image, multimodal reasoning, system prompts, and audio on the smaller variants. Google currently supports E2B and E4B directly with LiteRT-LM. (Google DeepMind)

Google's current LiteRT-LM reference models are approximately:

Gemma 4 E2B: 2.58 GB

Gemma 4 E4B: 3.65 GB

Google's own Android benchmarks show E2B substantially lighter than E4B, although those numbers are from other flagship Android hardware rather than the POCO X7 Pro. (Google AI for Developers)

The POCO X7 Pro has a Dimensity 8400-Ultra, Mali-G720 GPU, LPDDR5X RAM, UFS 4.0 storage, and comes in 8 GB or 12 GB RAM configurations. (Xiaomi)

Therefore my device policy would be:

8 GB POCO X7 Pro → Gemma 4 E2B

12 GB POCO X7 Pro → E2B default + optional E4B

Do not assume the MediaTek NPU will automatically outperform the GPU. Benchmark CPU/GPU/NPU during first-run capability detection and select the fastest stable backend. LiteRT provides accelerator-oriented CPU/GPU/NPU APIs, while MediaTek itself exposes heterogeneous CPU/GPU/NPU capabilities in the Dimensity platform. (Google AI for Developers)

2. What about MedGemma?

For the medical portion, MedGemma 1.5 4B IT is currently the much more relevant model than a generic medical fine-tune from an unknown repository.

It is a multimodal 4B instruction-tuned model derived from Gemma 3 and further trained for medical text and image comprehension. Its training/evaluation includes medical text, EHR information, dermatology, ophthalmology, histopathology, X-rays, CT, MRI, and medical-document understanding. (Google for Developers)

However, I would not make MedGemma the MVP Android runtime because Google's current Android deployment documentation is much stronger for Gemma 4 + LiteRT-LM than for MedGemma 1.5.

Use this roadmap instead:

MVP Gemma 4 E2B/E4B + Medical RAG + Prescription rule engine + specialized image models ↓ later Validated Medical Pack MedGemma 1.5 4B

MedGemma is particularly interesting for skin images because dermatology data is explicitly part of its medical training. Dental photography is not listed among its documented major training modalities, so I would not claim reliable dental diagnosis from MedGemma without a dedicated dental dataset and validation study. (Google for Developers)

3. Complete Android stack

Application

Language └── Kotlin UI ├── Jetpack Compose ├── Material 3 ├── Navigation Compose └── Adaptive layouts Architecture ├── Clean Architecture ├── MVVM + UDF ├── ViewModel ├── StateFlow ├── Coroutines └── Repository Pattern Dependency Injection └── Hilt Persistence ├── Room ├── SQLite ├── DataStore └── app-private filesystem AI ├── LiteRT-LM ├── LiteRT ├── Gemma 4 E2B/E4B ├── optional MedGemma ├── Gecko embeddings └── custom medical LiteRT models RAG ├── DocumentImporter ├── DocumentParser ├── Chunker ├── GeckoEmbedder ├── VectorRepository ├── Retriever ├── Reranker └── ContextAssembler Documents ├── Android Storage Access Framework ├── PDF ├── TXT ├── Markdown └── DOCX later Images ├── Android Photo Picker ├── CameraX ├── LiteRT └── Gemma/medical vision model Downloads ├── WorkManager ├── OkHttp ├── HTTP Range ├── ETag / If-Range ├── SHA-256 └── atomic .part → model rename Security ├── Android Keystore ├── app-private storage ├── encrypted sensitive metadata ├── no analytics by default └── no medical content leaving device Testing ├── JUnit ├── kotlinx-coroutines-test ├── Turbine ├── MockK/Fakes ├── Robolectric ├── Compose UI Test ├── Macrobenchmark └── Baseline Profiles Quality ├── Android Lint ├── Detekt ├── ktlint ├── Kover └── Gradle dependency verification

4. Recommended project structure

Do not over-modularize the MVP.

Something like this is sufficient:

app/ core/ ├── common/ ├── model/ ├── database/ ├── datastore/ ├── network/ ├── ai/ ├── rag/ └── ui/ feature/ ├── chat/ ├── documents/ ├── models/ ├── persona/ ├── image-analysis/ ├── prescription/ └── settings/

Then:

core:ai ├── LlmEngine.kt ├── LiteRtLlmEngine.kt ├── ModelRegistry.kt ├── ModelLoader.kt └── DeviceCapabilityDetector.kt core:rag ├── DocumentParser.kt ├── Chunker.kt ├── Embedder.kt ├── VectorStore.kt ├── Retriever.kt ├── CitationBuilder.kt └── RagOrchestrator.kt

Avoid an interface for every class.

Use abstractions only at actual boundaries:

LlmEngine Embedder VectorStore DocumentParser ModelDownloader PrescriptionValidator

That keeps SOLID without creating unnecessary architecture.

5. Model download architecture

This is particularly important for your requirement:

Wi-Fi disappears while downloading → download automatically continues after Wi-Fi returns.

Do not simply download the 2–4 GB model inside your Activity or ViewModel.

Use:

User presses Download ↓ ModelDownloadCoordinator ↓ WorkManager ↓ ResumableDownloadWorker ↓ OkHttp ↓ model.litertlm.part ↓ SHA-256 verification ↓ model.litertlm

WorkManager can enforce an unmetered-network constraint. If the constraint becomes unavailable while work is running, Android stops the worker and retries it after its constraints become satisfied again. (Android Developers)

WorkManager also provides persistent progress reporting that can be observed from your Compose UI. (Android Developers)

For true download resumption, additionally implement HTTP range downloading.

Persist:

ModelDownloadState( modelId, url, expectedBytes, downloadedBytes, etag, sha256, status )

Then request:

Range: bytes=1839281024- If-Range: "<previous-etag>"

Store the partial file as:

models/ └── downloads/ └── gemma4-e2b.litertlm.part

After completion:

1. verify total length 2. verify SHA-256 3. verify model manifest 4. initialize model once 5. atomic rename 6. mark INSTALLED

Never expose a half-downloaded model to LlmEngine.

6. Model manifest

Do not hard-code download information across UI classes.

Use one manifest:

{ "id": "gemma4-e2b", "displayName": "Gemma 4 E2B", "version": "2026.07", "format": "litertlm", "sizeBytes": 2580000000, "minimumRamMb": 7000, "multimodal": true, "sha256": "...", "url": "...", "recommendedBackend": "AUTO" }

You can support:

MODEL_NOT_INSTALLED DOWNLOADING PAUSED_NETWORK VERIFYING INSTALLED CORRUPTED UPDATE_AVAILABLE

The exact same infrastructure can download:

LLMs

embeddings

OCR

skin models

prescription classifiers

rerankers

This satisfies DRY.

7. SAF document upload

For RAG documents use Android's Storage Access Framework.

ACTION_OPEN_DOCUMENT is specifically intended for cases where the application wants persistent access to a user-selected document. (Android Developers)

Recommended flow:

Add Document ↓ ACTION_OPEN_DOCUMENT ↓ ContentResolver ↓ validate MIME + file size ↓ copy into app-private document store ↓ extract text ↓ normalize ↓ chunk ↓ embed ↓ index

Even though SAF can retain URI permission, I recommend copying the imported knowledge document into the application's private storage.

Why?

Because your RAG database should not break simply because the user later moves the original file.

Store original metadata:

documentId displayName sourceUri mimeType sha256 importDate version pageCount status

8. RAG implementation

Google's older AI Edge RAG SDK already demonstrated an entirely local Android pipeline using:

document → chunks → Gecko embedding → SQLite vector store → retrieval → LLM

The sample uses the Gecko 110M embedding model locally and stores 768-dimensional embeddings in SQLite. However, that RAG SDK is now marked deprecated, so I would copy the architectural pattern rather than couple a new project to the deprecated SDK. (Google AI for Developers)

Your implementation:

Question ↓ QueryNormalizer ↓ GeckoEmbedder ↓ VectorSearch ↓ Top 20 candidates ↓ metadata filtering ↓ reranking ↓ Top 4–8 chunks ↓ ContextAssembler ↓ Gemma

Start with SQLite.

Do not immediately introduce a giant vector database.

For your MVP:

Room + SQLite vector storage

is enough.

Google's original local RAG reference itself uses SQLite for persistent vector storage. (Google AI for Developers)

If your index grows substantially, migrate the implementation behind:

interface VectorStore

without touching the rest of the application.

9. Medical RAG needs metadata

Medical retrieval should not be based on cosine similarity alone.

Every chunk should contain:

document documentVersion publisher publicationYear section chapter page country specialty evidenceType drugName ICDCode effectiveDate

Example:

source = "PNPK Diabetes Mellitus" page = 72 section = "Pharmacological Management" published = 2024

Then responses should contain citations:

Metformin may be considered... Sources: [1] PNPK Diabetes Mellitus — p.72 [2] National Formulary — section ...

For a medical RAG application, provenance is more important than maximizing context length.

10. RAG grounding policy

Use a strict prompt contract:

Answer using retrieved medical sources. If adequate evidence is unavailable: - state that the local knowledge base is insufficient; - do not invent doses; - do not invent contraindications; - ask for required clinical information. Every clinical assertion must reference retrieved evidence.

Then implement a confidence gate:

retrievalScore < threshold ↓ INSUFFICIENT_EVIDENCE

The LLM should not answer using parametric memory as though it were current clinical guidance.

11. Persona AI

Gemma 4 now has native system-role support, which fits your configurable persona requirement. (Hugging Face)

Store persona separately:

PersonaEntity ├── id ├── name ├── systemPrompt ├── temperature ├── verbosity └── enabled

Prompt assembly:

SYSTEM SAFETY POLICY ↓ APPLICATION MEDICAL POLICY ↓ USER PERSONA ↓ RAG CONTEXT ↓ CONVERSATION ↓ USER MESSAGE

Critical rule:

user persona NEVER overrides medical safety policy

For example, a user persona saying:

Always provide a definitive diagnosis.

must not defeat the safety layer.

12. Photo upload and wound/skin analysis

Use Android Photo Picker or CameraX.

The system should become:

Photo ↓ Image preprocessing ↓ quality validation ↓ medical vision model ↓ possible findings ↓ clinical RAG retrieval ↓ LLM explanation

Do not do:

photo → LLM → final diagnosis

MedGemma 1.5 has documented medical-image capabilities and specifically includes dermatology imagery among its training/evaluation domains. Google nevertheless requires application-specific validation and explicitly says its outputs should not directly determine clinical diagnosis or treatment. (Google for Developers)

For image-only medical tasks, Google's own documentation recommends MedSigLIP rather than using MedGemma purely as an image classifier. (Hugging Face)

Therefore use:

Skin / wound image ↓ MedSigLIP ↓ validated classifier ↓ finding probabilities ↓ Gemma + RAG ↓ explanation

For example:

Possible finding: Inflammatory appearance Confidence: 0.72 Unable to determine: infection / benign lesion / malignancy Red flags detected: rapid enlargement bleeding fever necrosis

Not:

"You have melanoma."

13. Prescription architecture

This is the most important safety part.

Never let the LLM directly invent prescriptions.

Instead:

Gemma ↓ Structured recommendation ↓ PrescriptionSafetyEngine ↓ Drug database ↓ contraindications ↓ interaction checking ↓ dose limits ↓ clinician confirmation ↓ PrescriptionDraft

Have the model generate JSON:

{ "candidateDrugId": "RX-...", "indication": "...", "requestedDose": { "value": 500, "unit": "mg" }, "frequency": "...", "reason": "...", "sourceIds": ["doc-101:p72"] }

Then your deterministic engine verifies:

Patient age Weight Pregnancy Allergies Kidney function Liver function Current medications Drug interactions Indication Maximum dose Frequency Route Contraindications

Only then show:

Prescription Draft

not:

Prescription approved by AI

The final approval should require a clinician.

This follows the model provider's own limitation that MedGemma's output is not intended to directly determine treatment recommendations or patient management. (Google for Developers)

14. Recommended chat orchestration

A query should follow:

User ↓ Input classifier ↓ Emergency/red-flag detector ↓ Query understanding ↓ Need image? ↓ Need medical RAG? ↓ Retrieve evidence ↓ LLM ↓ Structured answer verifier ↓ Safety verifier ↓ Citation validator ↓ UI

For medications:

LLM suggestion ↓ DrugSafetyEngine ↓ PrescriptionDraft

15. Offline architecture

After model installation, nothing clinical should require Internet.

Internet │ └── model download only DEVICE ┌─────────────────────────────┐ │ Jetpack Compose │ │ │ │ Gemma 4 │ │ LiteRT-LM │ │ Gecko embeddings │ │ Medical vision models │ │ RAG documents │ │ SQLite / Room │ │ Persona │ │ Chat history │ │ Prescription rules │ └─────────────────────────────┘

Android's own offline-first architecture guidance recommends keeping local storage as the source of truth where network-backed resources exist. (Android Developers)

For your application that philosophy should be even stricter:

medical data → local only models → network during installation inference → local only RAG → local only images → local only

16. Clean-code rules

For this project I would enforce:

SOLID

Dependencies point inward:

Compose ↓ Use Cases ↓ Domain ↑ Infrastructure implementations

DRY

One:

ModelManager

rather than:

GemmaDownloader EmbeddingDownloader SkinModelDownloader OcrDownloader

YAGNI

Do not initially add:

microservices

cloud databases

Kubernetes

remote inference

complicated agent frameworks

five vector databases

multiple LLM engines

multi-agent orchestration

KISS

For MVP:

1 LLM 1 embedding model 1 local DB 1 vector store 1 RAG pipeline 1 prescription validator

17. Quality gates

I would make this the merge requirement:

./gradlew lint ./gradlew detekt ./gradlew ktlintCheck ./gradlew test

And require:

0 Android Lint errors 0 Detekt errors 0 ktlint errors all unit tests pass no hardcoded secrets no main-thread I/O no main-thread inference no main-thread document parsing

Important tests:

RAG retrieval tests citation correctness drug interaction tests contraindication tests dose boundary tests model corruption tests download resume tests Wi-Fi disconnect tests low-storage tests model OOM tests prompt injection tests malicious RAG document tests

18. Benchmark specifically on the POCO X7 Pro

Do not rely on generic benchmark claims.

Create:

DeviceBenchmarkRunner

Collect:

time-to-first-token tokens/sec peak RSS model-load time prompt-processing speed GPU temperature battery consumption OOM rate

Run:

Gemma E2B CPU Gemma E2B GPU Gemma E4B CPU Gemma E4B GPU

LiteRT-LM currently supports CPU/GPU/NPU execution, but accelerator behavior depends on hardware/runtime combinations, so this on-device profiling is essential. (Google AI for Developers)

19. Development steps

Phase 1 — Application foundation

Create:

Compose Hilt Room DataStore Navigation Coroutines Clean Architecture

Implement screens:

Home Chat Knowledge Base Models Persona Medical Image Settings

Phase 2 — Model Manager

Implement:

ModelManifest ModelRegistry ModelDownloadWorker ModelVerifier ModelStorage DeviceCapabilityDetector

Test:

Wi-Fi off during download Wi-Fi on again download resumes kill app reopen download continues reboot WorkManager retries

WorkManager is designed for persistent work across process/device lifecycle changes. (Android Developers)

Phase 3 — Gemma

Start with:

Gemma 4 E2B + LiteRT-LM

LiteRT-LM's Android Kotlin API provides multimodality and accelerator support, and its current Gemma 4 integration explicitly supports E2B and E4B. (Google AI for Developers)

Implement:

interface LlmEngine { fun generate(request: LlmRequest): Flow<LlmToken> }

Never let Compose know which runtime you use.

Phase 4 — SAF + RAG

Implement:

SAF ↓ parser ↓ chunker ↓ Gecko ↓ SQLite ↓ Retriever

Start with:

TXT Markdown PDF

Then add DOCX.

Do not support twenty document formats in version one.

Phase 5 — Evidence-first responses

Return:

Answer Confidence Sources Warnings Missing information

A medical answer without a supporting document should visibly say:

Local knowledge base has insufficient evidence.

Phase 6 — Persona

Add:

persona create persona edit persona delete persona enable

But isolate the safety system prompt from persona content.

Phase 7 — Images

Add:

Photo Picker CameraX image preprocessing medical vision model Gemma multimodal

Start with:

skin / wound assistance

Do not advertise general radiology/dental diagnosis until independently validated.

Phase 8 — Medication module

Build a structured local medication database.

Drug Indication DoseRule Contraindication Interaction PregnancyRule RenalAdjustment HepaticAdjustment

Then implement:

PrescriptionRecommendation PrescriptionSafetyEngine PrescriptionDraft

Phase 9 — Clinical safety validation

Create a test set maintained separately from RAG/training data.

Measure:

diagnostic suggestion accuracy dangerous omission rate false reassurance rate drug-selection error dose error interaction miss rate citation correctness unsupported-claim rate

The last three are arguably more important than chatbot-style benchmark scores.

20. My recommended MVP specification

For POCO X7 Pro minimum hardware, I would ship version 1 like this:

Android 12+ Jetpack Compose Kotlin Clean Architecture MVVM/UDF Hilt Room DataStore LiteRT-LM Gemma 4 E2B IT Gecko 110M quantized SQLite vector search PDF/TXT/MD RAG SAF import Persona system prompts Photo Picker Gemma multimodal optional MedSigLIP medical vision pack WorkManager OkHttp resumable downloads SHA-256 model verification Structured prescription draft deterministic medication safety engine 100% offline inference

Then provide an optional High Quality AI Pack:

Gemma 4 E4B

for 12 GB devices.

And later an experimental:

Medical Specialist Pack MedGemma 1.5 4B

after you have completed device conversion, memory benchmarking, and clinical validation.

21. Top 5 GitHub repositories to study

RankRepositoryWhy1google-ai-edge/LiteRT-LMMost important reference for Android local LLM inference, multimodality and acceleration. (GitHub)2google-ai-edge/galleryExcellent real-world Kotlin Android example for downloading and running local models and Gemma 4. (GitHub)3google-ai-edge/litert-samplesOfficial LiteRT/LiteRT-LM Android samples, conversion recipes and multimodal examples. (GitHub)4Google-Health/medgemmaOfficial medical model examples and medical fine-tuning references. (GitHub)5android/nowinandroidOne of the best references for modern Kotlin + Jetpack Compose architecture and modularization. (GitHub)

I would particularly study Google AI Edge Gallery before writing your own ModelManager. It is already an Android/Kotlin application designed around downloading and running local open models, including current Gemma models. (GitHub)

Final stack recommendation

The architecture I would choose in 2026 is:

┌───────────────────────────────────────┐ │ JETPACK COMPOSE UI │ └──────────────────┬────────────────────┘ │ ViewModel / StateFlow │ ┌──────────────────▼────────────────────┐ │ DOMAIN LAYER │ │ Chat / RAG / Image / Prescription │ └──────┬───────────┬───────────┬────────┘ │ │ │ ▼ ▼ ▼ LiteRT-LM RAG Safety Engine │ │ │ Gemma 4 Gecko Drug Rules E2B/E4B │ │ SQLite Vector │ │ └─────┬─────┘ │ Context Builder │ ▼ Evidence-grounded AI response │ Citation checker │ Safety validator │ ▼ Compose UI

The key design decision is not to make the LLM itself the medical system. Make the LLM one component inside a system containing RAG evidence, structured medication rules, image models, citations, confidence handling, emergency red-flag rules, and clinician confirmation.

That architecture gives you far better SOLID/DRY/YAGNI/KISS compliance, offline operation, maintainability, testability, and medical safety than building a monolithic "prompt → LLM → diagnosis → prescription" chatbot. It also lets you replace Gemma 4 E2B with E4B, MedGemma, or a future LiteRT-compatible medical model without rewriting the Android application.

Because LiteRT-LM and the mobile Gemma family are evolving quickly, I can also monitor major Android/on-device model updates that could materially improve this stack.

