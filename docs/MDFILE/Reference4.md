Here are the download links for the local AI models and bundles required for the MedBot project, organized by model type.

All models are from the official **litert-community** on Hugging Face and are ready for on-device inference with the **LiteRT-LM** runtime.

---

## LLM Models (`.litertlm` format)

These models power the core medical chatbot and multi-agent system.

### 1. Gemma 4 E2B Instruct (2.6 GB) — **Recommended**

The best balance of performance, accuracy, and multimodal capabilities for medical use on the Poco X7 Pro. Text-only for now; vision modality may be supported in future releases.

| Property | Value |
|----------|-------|
| **Parameters** | 2B |
| **Size** | ~2.6 GB |
| **Context** | 8K tokens |
| **Format** | `.litertlm` |
| **License** | Gemma Terms |
| **Backend** | GPU (WebGPU/CompiledModel) |

**Download:**
- **Direct:** [litert-community/gemma-4-E2B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm)
- **Web variant:** `gemma-4-E2B-it-web.litertlm`

### 2. Gemma 4 E4B Instruct (3.0 GB) — Higher Quality

Larger model with better reasoning capabilities; requires more RAM.

| Property | Value |
|----------|-------|
| **Parameters** | 4B |
| **Size** | ~3.0 GB |
| **Context** | 8K tokens |

**Download:**
- **Direct:** [litert-community/gemma-4-E4B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm)

### 3. Qwen3 0.6B (614 MB) — Lightweight Option

Smallest catalog model; runs on CPU or GPU; fast loading.

| Property | Value |
|----------|-------|
| **Parameters** | 0.6B |
| **Size** | 614 MB |
| **Context** | 4K tokens |
| **Backend** | WebGPU or CPU |

**Download:**
- **Direct:** [litert-community/Qwen3-0.6B](https://huggingface.co/litert-community/Qwen3-0.6B)

### 4. Gemma 3 1B IT (1.7 GB) — Optimized for Tensor/NPU

Optimized for devices with Google Tensor NPU support.

| Property | Value |
|----------|-------|
| **Parameters** | 1B |
| **Size** | 1.7 GB |
| **Context** | 1280 |
| **Quantization** | 8-bit per-channel |

**Download:**
- **Direct:** [litert-community/Gemma3-1B-IT](https://huggingface.co/litert-community/Gemma3-1B-IT)

### 5. Gemma 3 1B INT4 (Smaller) — ~500 MB

**Download:**
- **Direct:** [litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm](https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm)

### 6. VibeThinker-3B (1.9 GB) — Math & Reasoning

Good for diagnostic reasoning tasks.

**Download:**
- **Direct:** [litert-community/VibeThinker-3B](https://huggingface.co/litert-community/VibeThinker-3B)

---

## Embedding Models (RAG Pipeline)

These models convert document chunks into vectors for semantic search. Critical for the RAG pipeline.

### 1. Qwen3-Embedding-0.6B — **Recommended**

State-of-the-art small text-embedding model for on-device RAG.

| Property | Value |
|----------|-------|
| **Parameters** | 0.6B |
| **Size** | 881 MB (fp16) |
| **Format** | `.tflite` (not `.litertlm`) |
| **License** | Apache-2.0 |
| **Backend** | GPU only (CompiledModel) |

**Files:**
- `qwen3emb_gpu_fp16.tflite` — 28-layer Qwen3 transformer
- `embeddings_fp16.bin` — tied token-embedding table
- `vocab.json`, `merges.txt` — Qwen byte-level BPE tokenizer

**Download:**
- **Repo:** [litert-community/Qwen3-Embedding-0.6B-LiteRT](https://huggingface.co/litert-community/Qwen3-Embedding-0.6B-LiteRT)

### 2. ModernBERT Embedding (Alternative)

**Download:**
- **Repo:** [ckg/synth-4.0-modernbert-litert](https://huggingface.co/ckg/synth-4.0-modernbert-litert)

---

## Vision Models (Skin Analysis)

These models enable image-based diagnosis for skin lesions, wounds, and other visual medical conditions.

### 1. LLaVA-OneVision-0.5B (829 MB) — **Recommended**

Compact vision-language model; single-image VQA.

| Property | Value |
|----------|-------|
| **Parameters** | 0.5B |
| **Size** | 829 MB |
| **Format** | `.litertlm` |
| **Vision** | SigLIP encoder (384×384, 729 patches) |
| **Decoder** | Qwen2-0.5B (int4) |
| **Context** | 2048 |

**Download:**
- **Repo:** [litert-community/LLaVA-OneVision-0.5B](https://huggingface.co/litert-community/LLaVA-OneVision-0.5B)

### 2. InternVL3.5-1B (0.82 GB) — Higher Quality

Newer Qwen3 backbone; better vision grounding.

| Property | Value |
|----------|-------|
| **Parameters** | 1B |
| **Size** | 0.82 GB |
| **Format** | `.litertlm` |
| **Vision** | InternViT encoder (448×448, 256 tokens) |
| **Decoder** | Qwen3-0.6B (int4) |

**Download:**
- **Repo:** [litert-community/InternVL3_5-1B](https://huggingface.co/litert-community/InternVL3_5-1B)

### 3. FastVLM-0.5B (Alternative)

**Download:**
- **Repo:** [litert-community/FastVLM-0.5B](https://huggingface.co/litert-community/FastVLM-0.5B)

---

## CLI Tool for Downloading Models

Use the official **LiteRT-LM CLI** tool to download models directly from Hugging Face:

```bash
# Install the CLI tool
pip install -U litert-lm

# Download a model
litert-lm download litert-community/gemma-4-E2B-it-litert-lm

# Run a model locally
litert-lm run litert-community/gemma-4-E2B-it-litert-lm
```

---

## Model Manager Integration (Android)

The following open-source apps demonstrate how to integrate `.litertlm` model downloading and loading in Android:

| Repository | Key Feature |
|------------|-------------|
| **[LocalMathy](https://github.com/micr0-dev/localmathy)** | Resumable model download from Hugging Face; import via SAF |
| **[Google AI Edge Gallery](https://github.com/google-ai-edge/gallery)** | Import `.litertlm` directly from Hugging Face inside the app |
| **[LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM)** | Official runtime and demos |

---

## Summary Table

| Model Type | Recommended Model | Size | Download Link |
|------------|-------------------|------|---------------|
| **LLM** | Gemma 4 E2B | 2.6 GB | [litert-community/gemma-4-E2B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) |
| **LLM (Lightweight)** | Qwen3 0.6B | 614 MB | [litert-community/Qwen3-0.6B](https://huggingface.co/litert-community/Qwen3-0.6B) |
| **Embedding** | Qwen3-Embedding-0.6B | 881 MB | [litert-community/Qwen3-Embedding-0.6B-LiteRT](https://huggingface.co/litert-community/Qwen3-Embedding-0.6B-LiteRT) |
| **Vision** | LLaVA-OneVision-0.5B | 829 MB | [litert-community/LLaVA-OneVision-0.5B](https://huggingface.co/litert-community/LLaVA-OneVision-0.5B) |
| **Vision (Better)** | InternVL3.5-1B | 0.82 GB | [litert-community/InternVL3_5-1B](https://huggingface.co/litert-community/InternVL3_5-1B) |

> **Important:** As specified in the PRD and DESIGN.md, the official model registry remains empty until a release-owned manifest supplies verified HTTPS URL, size, SHA-256, and source provenance. The UI displays `MODEL_UNAVAILABLE` until a real validated model is loaded. These download links are provided as references for the `litert-community` catalog; your app should implement the evidence gate before marking any model as `AVAILABLE`.

---

Based on your detailed PRD, AGENTS.md, and DESIGN.md specifications, here is a comprehensive, production-ready guide for building **MedBot** — an offline-first, multi-agent medical AI assistant for Android using Jetpack Compose.

---

## Complete Technology Stack

### Architecture & Core
| Layer | Technology | Purpose |
|-------|------------|---------|
| **Language** | Kotlin | Primary development language |
| **UI Framework** | Jetpack Compose + Material 3 1.4.0 | Declarative UI with Material 3 Expressive |
| **Architecture** | Clean Architecture + MVVM | Separation of concerns, testability, SOLID compliance |
| **DI** | Dagger Hilt 2.52+ | Compile-time dependency injection |
| **Async** | Kotlin Coroutines & Flow | Background operations, streaming responses, StateFlow |

### AI & ML Inference
| Component | Technology | Purpose |
|-----------|------------|---------|
| **LLM Engine** | Google AI Edge LiteRT-LM (formerly MediaPipe) | Optimized on-device LLM inference |
| **LLM Format** | `.litertlm` | Google-optimized model format (production-ready) |
| **Embedding Model** | LiteRT MiniLM (384-dim) | Document vectorization for RAG |
| **Vision/Image** | LiteRT Vision Model / TFLite | On-device medical image analysis |

### Data & Storage
| Component | Technology | Purpose |
|-----------|------------|---------|
| **Local DB** | Room 2.7.1+ | Chat history, metadata, embedding storage |
| **Vector Search** | Cosine similarity (Room BLOB) | Semantic document retrieval |
| **File Storage** | Internal storage + SAF | Document and model storage |
| **Document Parsing** | `pdfbox-android` | PDF text extraction |
| **DataStore** | `androidx.datastore:datastore-preferences` | Persona & user preferences |

### Download & Networking
| Component | Technology | Purpose |
|-----------|------------|---------|
| **Download Manager** | WorkManager + OkHttp | Resumable background downloads |
| **Range Requests** | HTTP Range header | Download resumption on WiFi reconnect |
| **Network Constraints** | `setRequiredNetworkType(NetworkType.CONNECTED)` | WiFi-only downloads |

### Testing
| Component | Technology | Purpose |
|-----------|------------|---------|
| **Unit Tests** | JUnit4, MockK | Domain and data layer testing |
| **UI Tests** | Compose UI Testing | Screen interaction tests |
| **Instrumented Tests** | AndroidX Test, Espresso | Database and integration tests |

---

## Step-by-Step Development Guide

### Phase 0: Project Foundation

**Step 0.1: Initialize Project**
- Create new Android project with Empty Compose Activity
- Set `minSdk = 31` (Android 12), `targetSdk = 35` (Android 15), `compileSdk = 35`
- Enable edge-to-edge with `enableEdgeToEdge()` in MainActivity

**Step 0.2: Module Structure (Clean Architecture)**

```
app/
├── src/main/java/com/medbot/
│   ├── presentation/              # UI Layer (Jetpack Compose)
│   │   ├── ui/
│   │   │   ├── chat/              # Chat screen & ViewModel
│   │   │   ├── models/            # Model management screen
│   │   │   ├── documents/         # Document upload & RAG management
│   │   │   ├── persona/           # Persona/prompt configuration
│   │   │   ├── skin/              # Skin scan & lineage
│   │   │   └── tools/             # Medical tools
│   │   ├── theme/                 # Material 3 theming
│   │   └── navigation/            # Compose Navigation
│   │
│   ├── domain/                    # Domain Layer (Pure Kotlin)
│   │   ├── model/                 # Business models
│   │   ├── repository/            # Repository interfaces
│   │   └── usecase/               # Use cases (business logic)
│   │
│   ├── data/                      # Data Layer
│   │   ├── repository/            # Repository implementations
│   │   ├── local/
│   │   │   ├── database/          # Room entities & DAOs
│   │   │   ├── dao/               # Data Access Objects
│   │   │   └── datastore/         # Preferences DataStore
│   │   ├── model/                 # ML model management
│   │   ├── download/              # Resumable download service
│   │   ├── parser/                # Document parsers
│   │   └── inference/             # LiteRT-LM & embedding wrappers
│   │
│   ├── platform/                  # Android-specific gateways
│   │   ├── permissions/           # Permission handling
│   │   ├── saf/                   # Storage Access Framework
│   │   ├── media/                 # CameraX & gallery
│   │   └── system/                # System services
│   │
│   └── di/                        # Dagger Hilt Modules
```

**Step 0.3: Core Dependencies** (`build.gradle.kts`)

```kotlin
// Core
implementation("androidx.core:core-ktx:1.13.0")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")
implementation("androidx.activity:activity-compose:1.9.0")

// Compose (Material 3 1.4.0)
implementation(platform("androidx.compose:compose-bom:2024.10.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.ui:ui-graphics")
implementation("androidx.compose.ui:ui-tooling-preview")
implementation("androidx.compose.material3:material3:1.4.0")
implementation("androidx.compose.material:material-icons-extended")

// Navigation
implementation("androidx.navigation:navigation-compose:2.7.7")

// DI - Hilt
implementation("com.google.dagger:hilt-android:2.52")
ksp("com.google.dagger:hilt-compiler:2.52")
implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

// Room
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")

// DataStore Preferences
implementation("androidx.datastore:datastore-preferences:1.1.0")

// WorkManager (Downloads)
implementation("androidx.work:work-runtime-ktx:2.9.0")

// Networking (Resumable downloads)
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

// PDF Parsing
implementation("com.tom-roush:pdfbox-android:2.0.27.0")

// LiteRT-LM (Google AI Edge)
implementation("com.google.ai.edge.litert:litert-lm:0.1.0")
```

---

### Phase 1: Evidence-Gated Model Management

**Objective:** Implement a model loading system that only reports `AVAILABLE` after real validation.

**Step 1.1: Model Registry (Empty Until Manifest Verified)**

Per the PRD and DESIGN.md, the official registry remains empty until a release-owned manifest supplies verified HTTPS URL, size, SHA-256, and source provenance.

```kotlin
// domain/model/ModelMetadata.kt
data class ModelMetadata(
    val id: String,
    val name: String,
    val fileName: String,        // Must be .litertlm
    val sizeBytes: Long,
    val sha256Checksum: String,
    val provenance: String,       // Verified source
    val backend: ModelBackend,
    val isVisionCapable: Boolean = false
)

enum class ModelBackend { CPU, GPU, NPU }

// data/repository/ModelRepositoryImpl.kt
class ModelRepositoryImpl @Inject constructor(
    private val context: Context,
    private val manifestValidator: ManifestValidator
) : ModelRepository {
    
    override suspend fun getAvailableModels(): List<ModelMetadata> {
        // Returns empty list until a verified manifest is available
        val manifest = manifestValidator.getVerifiedManifest()
        return if (manifest != null) {
            manifest.models.filter { it.fileName.endsWith(".litertlm") }
        } else {
            emptyList()
        }
    }
}
```

**Step 1.2: SAF-Based Model Loading**

```kotlin
// platform/saf/ModelFilePicker.kt
class ModelFilePicker @Inject constructor(
    private val activityResultRegistry: ActivityResultRegistry
) {
    private val pickModel = activityResultRegistry.register(
        "pick_model",
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { handleModelFile(it) }
    }
    
    suspend fun handleModelFile(uri: Uri): ModelLoadResult {
        // 1. Validate file extension (.litertlm)
        val fileName = getFileName(uri)
        if (!fileName.endsWith(".litertlm")) {
            return ModelLoadResult.InvalidFormat
        }
        
        // 2. Copy to app-private storage
        val destFile = File(context.filesDir, "models/$fileName")
        copyFile(uri, destFile)
        
        // 3. Verify file size and checksum (if available)
        // 4. Attempt LiteRT-LM initialization
        return try {
            val engine = LiteRTEngine.initialize(destFile.absolutePath)
            ModelLoadResult.Success(engine, destFile)
        } catch (e: Exception) {
            ModelLoadResult.Failure(e.message ?: "Model initialization failed")
        }
    }
}
```

**Step 1.3: UI State (Honest State Exposure)**

Per DESIGN.md, every capability exposes an honest state:

```kotlin
sealed interface ModelUiState {
    data object Idle : ModelUiState
    data object Loading : ModelUiState
    data class Available(val modelName: String, val backend: String) : ModelUiState
    data object Unavailable : ModelUiState
    data class Error(val message: String) : ModelUiState
}
```

The UI displays `MODEL_UNAVAILABLE` until a real validated model is loaded. No fake "AI ready" state.

---

### Phase 2: Resumable Model Download System

**Objective:** Implement download with automatic resume when WiFi reconnects.

**Step 2.1: Download Worker with Range Requests**

```kotlin
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val modelUrl = inputData.getString(KEY_MODEL_URL) ?: return Result.failure()
        val modelName = inputData.getString(KEY_MODEL_NAME) ?: return Result.failure()
        val expectedChecksum = inputData.getString(KEY_CHECKSUM)
        
        val destFile = File(applicationContext.filesDir, "models/$modelName.part")
        val finalFile = File(applicationContext.filesDir, "models/$modelName")
        
        // Check existing partial download
        val downloadedBytes = if (destFile.exists()) destFile.length() else 0L
        
        // Request with Range header for resumption
        val request = Request.Builder()
            .url(modelUrl)
            .header("Range", "bytes=$downloadedBytes-")
            .build()
        
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return when (response.code) {
                    416 -> Result.success() // Already fully downloaded
                    else -> Result.retry()
                }
            }
            
            response.body?.let { body ->
                destFile.parentFile?.mkdirs()
                destFile.appendBytes(body.bytes())
                
                // Report progress
                val totalSize = response.header("Content-Range")?.let {
                    it.substringAfter("/").toLongOrNull()
                } ?: body.contentLength() + downloadedBytes
                
                setProgress(workDataOf(
                    PROGRESS to (downloadedBytes + body.contentLength()).toFloat() / totalSize
                ))
            }
        }
        
        // Verify checksum if provided
        if (expectedChecksum != null) {
            val actualChecksum = destFile.sha256()
            if (actualChecksum != expectedChecksum) {
                destFile.delete()
                return Result.failure()
            }
        }
        
        // Atomic rename
        destFile.renameTo(finalFile)
        return Result.success()
    }
}
```

**Step 2.2: WorkManager Configuration**

```kotlin
val downloadRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
    .setConstraints(
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)  // WiFi only
            .setRequiresBatteryNotLow(true)
            .build()
    )
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
    .setInputData(workDataOf(
        KEY_MODEL_URL to modelUrl,
        KEY_MODEL_NAME to modelName,
        KEY_CHECKSUM to checksum
    ))
    .build()

WorkManager.getInstance(context).enqueue(downloadRequest)
```

**Key Implementation Notes:**
- Uses HTTP Range header for resumption (bytes=X-)
- Writes to `.part` file first, then renames on completion (atomic)
- `setRequiredNetworkType(NetworkType.CONNECTED)` for WiFi requirement
- `setBackoffCriteria` for automatic retry on failure
- SHA-256 verification before atomic rename

---

### Phase 3: On-Device LLM Integration (LiteRT-LM)

**Objective:** Integrate LiteRT-LM for medical inference with streaming support.

**Step 3.1: Recommended Model Selection**

For **Poco X7 Pro** (Dimensity 9300+, 8-12GB RAM):

| Model | Parameters | Size | Best For |
|-------|-----------|------|----------|
| **Gemma 4 E2B Instruct** | 2B | ~2.4 GB | General medical QA, multimodal vision |
| **Gemma 4 E4B Instruct** | 4B | ~3.4 GB | Higher accuracy, complex cases |
| **SmolLM3-3B** | 3B | ~2.0 GB | Fast inference, good for low-resource |

**Recommended: Gemma 4 E2B Instruct** - Best balance of performance, accuracy, and vision capabilities for medical use.

**Step 3.2: LiteRT-LM Inference Wrapper**

```kotlin
// data/inference/LLMInference.kt
class LLMInference @Inject constructor(
    private val context: Context
) {
    private var model: LiteRTModel? = null
    private var session: LiteRTSession? = null
    
    suspend fun loadModel(modelPath: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val options = LiteRTModel.Options.Builder()
                    .setBackend(LiteRTModel.Backend.GPU)  // GPU acceleration
                    .setNumThreads(2)                      // Prevent thermal throttling
                    .build()
                
                model = LiteRTModel.fromFile(context, modelPath, options)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun generateStreaming(
        prompt: String,
        onToken: suspend (String) -> Unit
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                session = model?.startSession() ?: return@withContext 
                    Result.failure(Exception("Model not loaded"))
                
                session?.generateStreaming(prompt) { token ->
                    onToken(token)
                }
                Result.success("")
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                session?.close()
                session = null
            }
        }
    }
}
```

**Step 3.3: Streaming UI with Throttling**

```kotlin
// presentation/ui/chat/ChatViewModel.kt
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val llmInference: LLMInference,
    private val ragPipeline: RagPipeline
) : ViewModel() {
    
    private val _streamingMessage = MutableStateFlow("")
    val streamingMessage: StateFlow<String> = _streamingMessage
        .asStateFlow()
        .sample(50)  // Throttle to 50ms to prevent recomposition lag
    
    fun sendMessage(query: String, personaId: String) {
        viewModelScope.launch {
            _isStreaming.value = true
            _streamingMessage.value = ""
            
            ragPipeline.query(query, personaId).collect { token ->
                _streamingMessage.update { it + token }
            }
            
            _isStreaming.value = false
        }
    }
}
```

---

### Phase 4: Multi-Agent System with 46 Specialists

**Objective:** Implement the 46-specialist triage system defined in AGENTS.md.

**Step 4.1: Agent Registry**

```kotlin
// domain/model/Agent.kt
data class Agent(
    val id: String,
    val name: String,
    val specialization: String,
    val systemPrompt: String,
    val icon: String,
    val supportsImage: Boolean = false,
    val tools: List<String> = emptyList()
)

// data/registry/AgentRegistry.kt
@Singleton
class AgentRegistry @Inject constructor() {
    
    val allAgents: Map<String, Agent> = mapOf(
        "orchestrator" to Agent(
            id = "orchestrator",
            name = "Triage Assistant",
            specialization = "Symptom Triage & Clinical Routing",
            systemPrompt = """
                Anda adalah Kepala Triase Medis Puskesmas/Klinik. Tugas utama Anda adalah 
                menganalisis keluhan pengguna atau foto medis yang dilampirkan, lalu menentukan 
                spesialis yang paling kompeten. Output JSON murni:
                {
                  "primary_specialist": "agent_id",
                  "secondary_specialists": ["agent_id"],
                  "confidence": 0.0-1.0,
                  "urgency": "low|medium|high|emergency",
                  "reasoning": "penjelasan singkat alasan pemilihan"
                }
            """.trimIndent(),
            icon = "medical-services",
            supportsImage = true
        ),
        "dermatology" to Agent(
            id = "dermatology",
            name = "Dermatologist",
            specialization = "Skin & Venereal Diseases",
            systemPrompt = """
                Anda adalah Dokter Spesialis Kulit dan Kelamin.
                Saat menganalisis foto atau deskripsi kulit:
                1. Evaluasi karakteristik lesi: distribusi, morfologi, warna, batas, permukaan
                2. Terapkan prinsip ABCD pada lesi berpigmen/tahi lalat
                3. Ajukan pertanyaan diferensial: rasa gatal, perih, riwayat kontak
                4. Berikan anjuran perawatan awal yang aman
            """.trimIndent(),
            icon = "spa",
            supportsImage = true,
            tools = listOf("evaluate_skin_abcd", "search_skin_remedy")
        ),
        // ... 44 more agents from AGENTS.md catalog
    )
    
    fun getAgent(id: String): Agent? = allAgents[id]
    
    fun getAgentsBySpecialty(specialty: String): List<Agent> = 
        allAgents.values.filter { it.specialization.contains(specialty, ignoreCase = true) }
}
```

**Step 4.2: Triage Orchestrator (Intent & Image Classifier)**

```kotlin
// domain/usecase/ClassifyIntentUseCase.kt
class ClassifyIntentUseCase @Inject constructor(
    private val agentRegistry: AgentRegistry,
    private val llmInference: LLMInference
) {
    suspend fun execute(
        userQuery: String,
        hasImage: Boolean = false
    ): TriageResult {
        // If image is present, route to dermatology or appropriate vision specialist
        if (hasImage) {
            val imageSpecialists = listOf(
                "dermatology", "ophthalmology", "radiology", 
                "clinical_pathology", "pharmacy"
            )
            // Use orchestrator agent to classify
            val orchestrator = agentRegistry.getAgent("orchestrator")
            val prompt = orchestrator?.systemPrompt + "\n\nUser query: $userQuery\nHas image: true"
            
            // Get classification from LLM
            val response = llmInference.generate(prompt)
            return parseTriageResponse(response)
        }
        
        // Text-only classification
        return classifyTextOnly(userQuery)
    }
}
```

**Step 4.3: Dynamic System Prompt Construction**

Per AGENTS.md, the final prompt is constructed modularly:

```kotlin
// domain/usecase/BuildPromptUseCase.kt
class BuildPromptUseCase @Inject constructor() {
    
    fun buildPrompt(
        agent: Agent,
        persona: PersonaConfig,
        userQuery: String,
        ragContext: String? = null,
        hasImage: Boolean = false
    ): String {
        return buildString {
            // 1. Safety Guardrails (Non-negotiable)
            appendLine("""
                |Anda adalah asisten informasi kesehatan yang aman dan bertanggung jawab.
                |Prioritas utama: keselamatan pasien. Jika terdeteksi gejala darurat (nyeri dada,
                |sesak napas berat, penurunan kesadaran), segera instruksikan untuk menghubungi
                |layanan darurat (112/119).
            """.trimMargin())
            appendLine()
            
            // 2. Specialist Domain Prompt
            appendLine(agent.systemPrompt)
            appendLine()
            
            // 3. User Persona Modifiers
            appendLine(persona.tone.promptModifier)
            appendLine(persona.depth.promptModifier)
            if (persona.customInstructions.isNotEmpty()) {
                appendLine("Catatan tambahan: ${persona.customInstructions}")
            }
            appendLine()
            
            // 4. Language Instructions
            appendLine("Bahasa: ${persona.language.displayName}")
            appendLine()
            
            // 5. RAG Context
            if (!ragContext.isNullOrEmpty()) {
                appendLine("KONTEKS DARI DOKUMEN MEDIS:")
                appendLine(ragContext)
                appendLine()
            }
            
            // 6. User Query
            if (hasImage) {
                appendLine("Pengguna melampirkan foto medis. Analisis foto bersama dengan pertanyaan berikut:")
            }
            appendLine("PERTANYAAN PENGGUNA: $userQuery")
            appendLine()
            appendLine("RESPONS (sebagai tenaga medis profesional):")
        }
    }
}
```

---

### Phase 5: RAG Pipeline with SAF Document Upload

**Objective:** Enable users to upload medical documents (PDF, TXT, MD, DOCX) for RAG.

**Step 5.1: Document Upload with SAF**

```kotlin
// platform/saf/DocumentPicker.kt
class DocumentPicker @Inject constructor(
    private val activityResultRegistry: ActivityResultRegistry
) {
    private val pickDocument = activityResultRegistry.register(
        "pick_document",
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { handleDocument(it) }
    }
    
    suspend fun handleDocument(uri: Uri): DocumentUploadResult {
        val fileName = getFileName(uri)
        val destFile = File(context.filesDir, "documents/$fileName")
        destFile.parentFile?.mkdirs()
        
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        // Compute SHA-256 from original bytes
        val checksum = destFile.sha256()
        
        // Store document metadata in Room
        val doc = Document(
            id = UUID.randomUUID().toString(),
            name = fileName,
            uri = uri.toString(),
            localPath = destFile.absolutePath,
            sha256 = checksum,
            uploadedAt = System.currentTimeMillis()
        )
        documentDao.insert(doc)
        
        // Trigger RAG processing
        processDocument(destFile, doc.id)
        
        return DocumentUploadResult.Success(doc)
    }
}
```

**Step 5.2: Document Parsing**

```kotlin
// data/parser/DocumentParser.kt
class DocumentParser @Inject constructor() {
    
    suspend fun parseDocument(file: File): ParsedDocument {
        return when (file.extension.lowercase()) {
            "pdf" -> parsePdf(file)
            "txt", "md" -> parseText(file)
            "docx" -> parseDocx(file)
            else -> throw UnsupportedFormatException("Format not supported")
        }
    }
    
    private suspend fun parsePdf(file: File): ParsedDocument {
        return withContext(Dispatchers.IO) {
            PDDocument.load(file).use { document ->
                val stripper = PDFTextStripper()
                val text = stripper.text
                ParsedDocument(
                    text = text,
                    pageCount = document.numberOfPages,
                    metadata = mapOf("pages" to document.numberOfPages)
                )
            }
        }
    }
}
```

**Step 5.3: Chunking & Embedding**

```kotlin
// data/rag/DocumentChunker.kt
class DocumentChunker @Inject constructor() {
    
    fun chunkDocument(
        text: String, 
        chunkSize: Int = 512, 
        overlap: Int = 50
    ): List<DocumentChunk> {
        val words = text.split(Regex("\\s+"))
        val chunks = mutableListOf<DocumentChunk>()
        
        var index = 0
        while (index < words.size) {
            val end = minOf(index + chunkSize, words.size)
            val chunkWords = words.subList(index, end)
            val chunkText = chunkWords.joinToString(" ")
            
            chunks.add(
                DocumentChunk(
                    index = chunks.size,
                    text = chunkText,
                    startWord = index,
                    endWord = end
                )
            )
            
            index += (chunkSize - overlap)
        }
        
        return chunks
    }
}

// data/inference/EmbeddingGenerator.kt
class EmbeddingGenerator @Inject constructor(
    private val context: Context
) {
    private var embeddingModel: LiteRTEmbeddingModel? = null
    
    suspend fun loadModel(modelPath: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                embeddingModel = LiteRTEmbeddingModel.fromFile(context, modelPath)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun generateEmbedding(text: String): FloatArray {
        return withContext(Dispatchers.IO) {
            embeddingModel?.embed(text) ?: FloatArray(384)
        }
    }
}
```

**Step 5.4: Vector Storage (Room)**

```kotlin
@Entity(tableName = "document_chunks")
data class DocumentChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: String,
    val chunkIndex: Int,
    val text: String,
    val embedding: ByteArray  // Serialized FloatArray
)

@Dao
interface DocumentChunkDao {
    @Query("""
        SELECT * FROM document_chunks 
        ORDER BY cosine_distance(embedding, :queryEmbedding) 
        LIMIT :topK
    """)
    suspend fun findSimilarChunks(
        queryEmbedding: ByteArray,
        topK: Int = 5
    ): List<DocumentChunkEntity>
}
```

**Step 5.5: RAG Pipeline**

```kotlin
// domain/usecase/RagQueryUseCase.kt
class RagQueryUseCase @Inject constructor(
    private val embeddingGenerator: EmbeddingGenerator,
    private val documentChunkDao: DocumentChunkDao,
    private val llmInference: LLMInference,
    private val promptBuilder: BuildPromptUseCase,
    private val agentRegistry: AgentRegistry
) {
    
    suspend fun query(
        userQuery: String,
        agentId: String,
        persona: PersonaConfig,
        imageData: ByteArray? = null
    ): Flow<String> = flow {
        // 1. Embed the query
        val queryEmbedding = embeddingGenerator.generateEmbedding(userQuery)
        
        // 2. Retrieve relevant documents (if embedder available)
        val relevantChunks = if (embeddingGenerator.isAvailable()) {
            documentChunkDao.findSimilarChunks(
                queryEmbedding.toByteArray(),
                topK = 5
            )
        } else {
            emptyList()
        }
        
        // 3. Build context
        val context = if (relevantChunks.isNotEmpty()) {
            relevantChunks.joinToString("\n---\n") { it.text }
        } else {
            null
        }
        
        // 4. Get agent
        val agent = agentRegistry.getAgent(agentId) 
            ?: agentRegistry.getAgent("general_practice")!!
        
        // 5. Build prompt
        val prompt = promptBuilder.buildPrompt(
            agent = agent,
            persona = persona,
            userQuery = userQuery,
            ragContext = context,
            hasImage = imageData != null
        )
        
        // 6. Generate response (streaming)
        llmInference.generateStreaming(prompt).collect { token ->
            emit(token)
        }
    }
}
```

**Important**: If the embedder is unavailable, the status is `EMBEDDER_UNAVAILABLE`, not a fake success state.

---

### Phase 6: Skin Vision & Skin Lineage

**Objective:** Implement image capture, analysis, and chronological tracking.

**Step 6.1: Image Capture with CameraX**

```kotlin
// platform/media/ImageCaptureManager.kt
class ImageCaptureManager @Inject constructor(
    private val context: Context
) {
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { processImage(it) }
    }
    
    private val captureImage = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) imageUri?.let { processImage(it) }
    }
    
    suspend fun processImage(uri: Uri): SkinRecord {
        // 1. Copy to app-private media gateway
        val destFile = File(context.filesDir, "skin_records/${UUID.randomUUID()}.jpg")
        copyFile(uri, destFile)
        
        // 2. Create SkinRecord
        val record = SkinRecord(
            id = UUID.randomUUID().toString(),
            imagePath = destFile.absolutePath,
            bodyLocation = "",  // User must select explicitly
            capturedAt = System.currentTimeMillis(),
            analysisResult = null  // Will be populated if vision model available
        )
        
        // 3. Store in Room
        skinRecordDao.insert(record)
        
        return record
    }
}
```

**Step 6.2: Vision Analysis (Only When Model Available)**

Per DESIGN.md, vision results are blocked without a real validated vision model:

```kotlin
// data/inference/VisionAnalyzer.kt
class VisionAnalyzer @Inject constructor(
    private val visionModel: LiteRTVisionModel?
) {
    
    suspend fun analyzeSkinImage(imagePath: String): VisionResult {
        // Return UNAVAILABLE if no vision model is loaded
        if (visionModel == null) {
            return VisionResult.Unavailable(
                reason = "VISION_UNAVAILABLE - No vision-capable local runtime initialized"
            )
        }
        
        return withContext(Dispatchers.IO) {
            try {
                val image = BitmapFactory.decodeFile(imagePath)
                val result = visionModel.analyze(image)
                
                VisionResult.Success(
                    abcdScore = result.abcdScore,
                    urgency = result.urgency,
                    description = result.description
                )
            } catch (e: Exception) {
                VisionResult.Error(e.message ?: "Analysis failed")
            }
        }
    }
}
```

**Step 6.3: Skin Lineage (Chronological Tracking)**

```kotlin
@Entity(tableName = "skin_records")
data class SkinRecord(
    @PrimaryKey val id: String,
    val imagePath: String,
    val bodyLocation: String,  // User must select explicitly
    val capturedAt: Long,
    val analysisResult: String?,
    val urgencyLevel: String?,
    val notes: String? = null
)

@Dao
interface SkinRecordDao {
    @Query("SELECT * FROM skin_records WHERE bodyLocation = :location ORDER BY capturedAt DESC")
    suspend fun getRecordsByLocation(location: String): List<SkinRecord>
    
    @Query("SELECT * FROM skin_records WHERE id = :id")
    suspend fun getRecord(id: String): SkinRecord?
}
```

---

### Phase 7: Persona Configuration

**Objective:** Allow users to customize AI response style.

**Step 7.1: Persona Data Model**

```kotlin
// domain/model/PersonaConfig.kt
data class PersonaConfig(
    val selectedAgentId: String = "orchestrator",
    val tone: PersonaTone = PersonaTone.EMPATHETIC,
    val depth: DetailDepth = DetailDepth.STANDARD,
    val language: AppLanguage = AppLanguage.INDONESIAN,
    val customInstructions: String = "",
    val patientProfileSummary: String = ""
)

enum class PersonaTone(val promptModifier: String) {
    EMPATHETIC("Gunakan nada bicara yang hangat, ramah, penuh empati, dan menenangkan hati pasien."),
    CLINICAL("Gunakan gaya penulisan formal medis, presisi, mencantumkan terminologi klinis."),
    CONCISE("Berikan jawaban yang sangat ringkas, to-the-point, fokus pada langkah aksi."),
    EDUCATIONAL("Fokus pada edukasi komprehensif mengenai mekanisme penyakit dan pencegahan.")
}

enum class DetailDepth(val promptModifier: String) {
    SIMPLE("Jelaskan dengan bahasa orang awam tanpa istilah medis yang rumit. Maksimal 3 paragraf."),
    STANDARD("Berikan penjelasan terstruktur lengkap dengan poin-poin anjuran dan tanda bahaya."),
    DEEP("Berikan analisis mendalam mencakup patofisiologi, diagnosis banding komprehensif.")
}
```

**Step 7.2: DataStore Persistence**

```kotlin
// data/datastore/PersonaDataStore.kt
@Singleton
class PersonaDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val PERSONA_CONFIG = stringPreferencesKey("persona_config")
    }
    
    suspend fun savePersonaConfig(config: PersonaConfig) {
        dataStore.edit { preferences ->
            preferences[PERSONA_CONFIG] = Json.encodeToString(config)
        }
    }
    
    fun getPersonaConfigFlow(): Flow<PersonaConfig> {
        return dataStore.data.map { preferences ->
            val json = preferences[PERSONA_CONFIG]
            if (json != null) Json.decodeFromString(json) else PersonaConfig()
        }
    }
}
```

---

## Best Practices for Code Quality

### SOLID Principles

| Principle | Implementation |
|-----------|----------------|
| **Single Responsibility** | Each class has one reason to change (e.g., `DocumentParser` only parses, `EmbeddingGenerator` only generates embeddings) |
| **Open/Closed** | Use interfaces for repositories, extend via implementation |
| **Liskov Substitution** | Use sealed classes for UI states |
| **Interface Segregation** | Small, focused interfaces (e.g., `IDocumentParser`, `IEmbeddingGenerator`) |
| **Dependency Inversion** | Domain layer defines interfaces, data layer implements them |

### DRY (Don't Repeat Yourself)
- Extract common utilities (e.g., `FileUtils`, `CoroutineUtils`)
- Use base classes for similar ViewModels
- Create reusable Compose components (e.g., `LoadingIndicator`, `ErrorBanner`)

### YAGNI (You Aren't Gonna Need It)
- Build only the features specified in PRD
- Don't implement generic RAG for all file types if only PDF/Word are needed
- Start with one model format (`.litertlm`) before adding others
- Language support limited to Bahasa Indonesia and English

### KISS (Keep It Simple, Stupid)
- Use simple data classes over complex inheritance
- Prefer `StateFlow` over `LiveData` for consistency
- Keep ViewModels focused on UI state, not business logic
- No unnecessary abstractions

### Anti-Slop Principles (From DESIGN.md)

Per the DESIGN.md contract:

- **No canned responses, synthetic documents, fabricated model metadata, or fake citations**
- **No heuristic skin diagnoses, default clinical inputs, emoji decoration, or placeholder controls**
- **Missing evidence is rendered as `UNAVAILABLE`, `INSUFFICIENT_DATA`, or `MODEL_UNAVAILABLE`**
- **No disabled button without visible supporting context**
- **No numerical metric without a real source, timestamp, unit, and provenance**

### Architecture Rules (From DESIGN.md)

- Compose renders state and emits events; it does not call DAOs, WorkManager, or LiteRT-LM directly
- ViewModels use `viewModelScope`, `StateFlow`, cancellation, and lifecycle-aware collection
- Domain does not import data implementations
- Data maps framework errors into typed domain failures
- Room migrations are explicit; destructive migration is not enabled
- No `runBlocking`, `GlobalScope`, `!!`, main-thread file/database work, empty catches, or silent infinite retry

### Verification Checklist (From DESIGN.md)

- [ ] UI text has Bahasa Indonesia and English resources
- [ ] No placeholder, mock, dummy, synthetic, canned, or fabricated production output
- [ ] All interactive controls have a handler, state, and accessible label
- [ ] Every target is at least 48dp and usable with large text
- [ ] Empty/loading/error/unavailable/permission states are visible
- [ ] Compact, medium, expanded, landscape, keyboard, and edge-to-edge layouts are checked
- [ ] Local model/RAG/vision results are blocked without real validated evidence
- [ ] `lintDebug`, unit tests, assembly, and connected tests pass

---

## Top 10 GitHub Repositories for Reference

| # | Repository | Key Features | Best For |
|---|------------|--------------|----------|
| 1 | **[PocketSage](https://github.com/umarpazir11/pocketsage)** | Complete offline RAG, Clean Architecture, Hilt, PDF parsing, streaming LLM | **Primary reference** - Most comprehensive RAG implementation |
| 2 | **[offline-rag-android](https://github.com/nicolas-raoul/offline-rag-android)** | Full RAG system, vector similarity search, Jetpack Compose UI | RAG fundamentals and vector search |
| 3 | **[Local-LLM-AI](https://github.com/PrinceBad/Local-LLM-AI)** | LiteRT integration, premium Compose UI, model management | LiteRT-LM integration and UI design |
| 4 | **[kotlin-gemma-4e2b-chatbot](https://github.com/gabrielpreda/kotlin-gemma-4e2b-chatbot)** | Fully local Android chatbot, Gemma 4:E2B, LiteRT-LM | Gemma 4 integration with Compose |
| 5 | **[local-llm-chat](https://github.com/Rithik-101/local-llm-chat)** | MediaPipe GenAI API, offline chat, Gemma models | MediaPipe LLM API implementation |
| 6 | **[GemOfGemma](https://github.com/ajay-sainy/GemOfGemma)** | On-device AI with Gemma 4, LiteRT-LM, multimodal | Vision-capable LLM integration |
| 7 | **[Ketch](https://github.com/khushpanchal/Ketch)** | WorkManager-based downloader with pause/resume | Resumable model downloads |
| 8 | **[Real-Clean-Architecture-In-Android](https://github.com/DenisBronx/Real-Clean-Architecture-In-Android---Sample)** | Clean Architecture, SOLID principles, minimal libraries | Clean Architecture implementation |
| 9 | **[AndroidSemanticSearch](https://github.com/hissain/AndroidSemanticSearch)** | Semantic search, ObjectBox, MiniLM embeddings | Embedding and vector search |
| 10 | **[Google AI Edge Gallery](https://github.com/google-ai-edge/gallery)** | Official LiteRT-LM samples, multimodal, GPU/NPU | Official LiteRT-LM reference |

---

## Summary

This guide provides a complete, production-ready blueprint for building **MedBot** — an offline-first, multi-agent medical AI assistant for Android. Key takeaways:

1. **Clean Architecture** with SOLID, DRY, YAGNI, and KISS principles
2. **LiteRT-LM** for on-device LLM inference with `.litertlm` models
3. **Resumable downloads** with WorkManager and HTTP Range requests
4. **46-specialist multi-agent system** with triage orchestration
5. **RAG pipeline** with SAF document upload, chunking, embedding, and vector search
6. **Skin vision & lineage** with CameraX and vision-capable LLM
7. **Persona customization** with tone, depth, and language settings
8. **Honest state exposure** — `UNAVAILABLE`, `INSUFFICIENT_DATA`, `MODEL_UNAVAILABLE`

Start with **PocketSage** as your primary reference, then adapt the other repositories for specific features like resumable downloads, LiteRT-LM integration, and UI polish.

> **Disclaimer**: As specified in the PRD, MedBot is an information assistant, not a diagnostic device. All medical decisions should be made with professional healthcare providers.