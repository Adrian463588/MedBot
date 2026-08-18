Here is a comprehensive, step-by-step guide to building your offline-first medical AI assistant app on Android using Jetpack Compose.

This guide follows best practices, ensuring the code is clean, testable, and maintainable by adhering to SOLID, DRY, YAGNI, and KISS principles. It incorporates all your specified features: a local LLM, a RAG pipeline with user-uploaded documents, resumable model downloads, customizable AI personas, and image uploads for diagnostic analysis.

### Core Technology Stack

The stack is chosen for performance on the Poco X7 Pro (which features a MediaTek Dimensity 9300+ and ample RAM) and strict adherence to offline, privacy-first principles.

*   **Language:** Kotlin
*   **UI Framework:** Jetpack Compose with Material 3
*   **Architecture:** Clean Architecture with MVVM (Model-View-ViewModel)
*   **Dependency Injection:** Dagger Hilt
*   **Asynchronous Programming:** Kotlin Coroutines & Flow
*   **Local Database:** Room (for chat history & metadata)
*   **LLM Inference Engine:** llama.cpp via the `llama.cpp` Android library or Google's MediaPipe Tasks GenAI API.
*   **LLM Model Format:** GGUF or `.task` for MediaPipe.
*   **Recommended Local LLM:** **Gemma 2 2B** (or **Gemma 3 1B/4B**) Q4_K_M quantized GGUF or `Phi-3.5-mini-instruct` Q4_K_M GGUF. These models balance performance and accuracy perfectly for the target hardware.
*   **Embedding Model:** `all-MiniLM-L6-v2` exported to ONNX with INT8 quantization.
*   **ONNX Runtime:** ONNX Runtime Mobile for embedding generation.
*   **Vector Database:** `sqlite-vec` SQLite extension for local vector search.
*   **Document Parsing:** Apache PdfBox Android (for PDFs), Apache POI (for Word/Excel), and the system `DocumentFile` API.
*   **Image Analysis:** ML Kit Document Scanner API or CameraX for capturing images, and potentially a vision-capable local LLM like `Qwen 3.5 2B/4B` for analysis.
*   **Background Download:** WorkManager with OkHttp for resumable downloads.
*   **File Management:** Android Storage Access Framework (SAF).

---

### Development Roadmap: Step-by-Step Guide

#### Phase 1: Project Foundation & Architecture

1.  **Initialize Project:** Create a new Android project in Android Studio with an empty Compose activity. Set `minSdk` to 26 and `targetSdk` to 35.
2.  **Setup Clean Architecture:** Structure your project into three main modules:
    *   **`app` (Presentation Layer):** Contains UI (Composables), ViewModels, and Hilt injection.
    *   **`domain` (Domain Layer):** Contains use cases, repository interfaces, and business logic models (POJOs). This layer is pure Kotlin and has no Android dependencies.
    *   **`data` (Data Layer):** Implements the repository interfaces, handles data sources (Room, `sqlite-vec`, file system), and manages models.
3.  **Setup Dependencies:** Add necessary libraries to your `build.gradle.kts` files, including Jetpack Compose, Hilt, Room, Kotlin Coroutines, and `androidx.work:work-runtime-ktx`.

#### Phase 2: Resumable Model Download System

1.  **Model Repository:** Create a `ModelRepository` in the data layer to manage the list of available models (from a local or bundled catalog) and their download status.
2.  **Download Service:** Implement a `ModelDownloadWorker` that extends `androidx.work.CoroutineWorker`.
    *   Use OkHttp to download the `.gguf` or `.task` model file from Hugging Face or a custom URL.
    *   Implement download resumption by setting the `Range` header in your OkHttp request based on the existing file's size.
    *   Update the `ModelRepository` and notify the UI via `LiveData` or `Flow` on download progress.
    *   Set `setRequiredNetworkType(NetworkType.CONNECTED)` and `setBackoffCriteria` in the `WorkRequest` to handle retries.
3.  **UI Trigger:** Create a Compose screen (e.g., `ModelManagementScreen`) that lists available models and allows the user to trigger the download with a button. The button's state should reflect the download status (e.g., "Download", "Downloading...", "Loaded").

#### Phase 3: On-Device LLM Integration

1.  **LLM Wrapper:** Create a `LLMInference` class (likely a singleton in the data layer). This class will manage the lifecycle of the inference engine using **llama.cpp** or **MediaPipe**.
    *   *For llama.cpp:* Use the Android library to load the GGUF model from the downloaded file path.
    *   *For MediaPipe:* Set up `LlmInference` and create a `Session` for each conversation.
2.  **Streaming Integration:** In your ViewModel, call the LLM inference function from a coroutine. Collect the streaming results (tokens) and update a `StateFlow<String>`.
    *   *Best Practice:* Use `.sample(50)` on the StateFlow to throttle UI recompositions for performance.
3.  **Persona/Prompt System:** Store user-defined "persona" prompts in Room. When the user sends a message, the ViewModel constructs the final prompt by combining the persona with the user's question and the RAG context.

#### Phase 4: Document Upload & RAG Pipeline

1.  **Document Upload with SAF:** Use the Storage Access Framework (`ActivityResultLauncher` with `ACTION_OPEN_DOCUMENT`) to allow users to select documents.
    *   Copy the file to your app's private storage for reliable access and parsing.
2.  **Document Parsing:** Use `PdfBox-Android` for PDFs. For other formats (`.docx`, `.txt`), use appropriate parsers.
3.  **Chunking:** Create a `DocumentChunker` class. Split the extracted text into overlapping chunks (e.g., 512 tokens with a 50-token overlap) to respect memory limits.
4.  **Embedding Generation:**
    *   **Model Loading:** Load the quantized `.onnx` embedding model using ONNX Runtime Mobile.
    *   **Generation:** For each chunk, call the `embed(text: String)` function to generate a vector (e.g., 384-dimensional float array).
    *   *Optimization:* Set `intraOpNumThreads` to 2 to prevent thermal throttling.
5.  **Vector Storage:**
    *   **Setup `sqlite-vec`:** Include the `sqlite-vec` extension in your app's native libraries.
    *   **Database:** In your Room database, use a raw SQL query to create a virtual table for vector search.
    *   **Insertion:** Insert each chunk's text and its corresponding embedding vector into the `vec0` table.
6.  **Retrieval (RAG Query):**
    1.  When a user asks a question, embed the question using the same ONNX model.
    2.  Execute a vector similarity search query on the `vec0` table to find the top-K most relevant text chunks.
    3.  Pass the retrieved chunks as context to the LLM along with the user's question to generate a grounded answer.

#### Phase 5: Medical Image Upload & Analysis

1.  **Image Capture/Picker:** Use `CameraX` for taking a photo or `MediaStore` to pick one from the gallery.
2.  **Image Analysis:**
    *   **Option A (Vision LLM):** If you downloaded a vision-capable model (like `Qwen 3.5 2B/4B`)，you can attach the image to the chat and prompt it for a diagnosis.
    *   **Option B (Classic ML):** Use a smaller, specialized ML model (e.g., a TensorFlow Lite model for skin condition classification) to pre-analyze the image and provide a text summary. This summary is then fed into the RAG pipeline as context.

---

### Top 5 GitHub Repositories for Reference

These repositories are excellent references for implementing different parts of your application.

| Repository | Key Features & How It Helps |
| :--- | :--- |
| **[LocalMind](https://github.com/tk85457/LocalMind)** | The best all-in-one reference. It demonstrates a full offline chatbot with llama.cpp, Compose UI, RAG with PDF upload, background model downloads, and more. |
| **[LMPlayground](https://github.com/andriydruk/LMPlayground)** | Excellent for its **reliable background download engine** with automatic resume on network interruption. It also showcases RAG with support for various document types. |
| **[On-Device RAG for Android](https://github.com/nicolas-raoul/offline-rag-android)** | A focused and clear example of a complete RAG pipeline on Android. It demonstrates the core concepts of embedding, vector search, and LLM integration in a clean, educational manner. |
| **[local-llm-chat](https://github.com/Rithik-101/local-llm-chat)** | A great, simple example of integrating **MediaPipe's GenAI API** with Jetpack Compose. Perfect if you choose MediaPipe over a raw JNI implementation. |
| **[On-Device RAG Architecture Guide](https://dev.to/software_mvp-factory/on-device-rag-for-android-4a7g)** | A comprehensive **article and code guide** that walks you through building the RAG pipeline step-by-step. It covers ONNX Runtime for embeddings, `sqlite-vec` for vector search, and performance optimizations for mobile. |

---

### Best Practices for Code Quality & Offline Operation

*   **SOLID Principles:** Design your classes with a single responsibility. For example, a `DocumentParser` class only parses, a `VectorStore` class only handles database operations, and a `ChatViewModel` handles UI state.
*   **DRY, YAGNI, KISS:** Don't create overly complex generic systems. Implement only the features you need (like the specific document types and model formats) and keep your logic simple and straightforward.
*   **Clean Code:** Write small, well-named functions. Use meaningful variable names. Document complex logic, especially around the JNI calls for llama.cpp.
*   **Offline-First Design:** Ensure all database and file operations are performed on background threads using Coroutines. The UI should never block.
*   **Resource Management:** Always `close()` LLM sessions and ONNX runtime sessions to release GPU/NPU memory. Use `use` blocks for auto-closable resources.
*   **Error Handling:** Implement robust error handling for network interruptions, file I/O, and model loading failures. Provide clear, user-friendly error messages.
*   **Testing:** Write unit tests for your domain and data layer logic. Use instrumented tests for UI interactions and database operations.


# Complete Development Guide: Offline Medical AI Assistant for Android

## Executive Summary

This guide provides a complete blueprint for building a **production-grade offline medical AI assistant** on Android using Jetpack Compose. The application is designed for **3T regions** (frontier, outermost, and disadvantaged areas) where doctor access is limited but medicine warehouses are accessible via helicopter delivery. All AI processing runs **entirely offline** on the device, ensuring privacy and functionality without internet connectivity.

The target device is the **Poco X7 Pro** (MediaTek Dimensity 9300+, 8-12GB RAM), which provides sufficient compute for running 2B-4B parameter LLMs at usable speeds.

---

## Complete Technology Stack

### Architecture & Core
| Layer | Technology | Purpose |
|-------|------------|---------|
| **Language** | Kotlin | Primary development language |
| **UI Framework** | Jetpack Compose + Material 3 | Declarative UI with modern design |
| **Architecture** | Clean Architecture + MVVM | Separation of concerns, testability |
| **DI** | Dagger Hilt 2.58+ | Compile-time dependency injection |
| **Async** | Kotlin Coroutines & Flow | Background operations, streaming responses |

### AI & ML Inference
| Component | Technology | Purpose |
|-----------|------------|---------|
| **LLM Engine** | Google AI Edge LiteRT (formerly MediaPipe) | Optimized on-device LLM inference |
| **LLM Format** | `.litertlm` or `.task` | Google-optimized model format |
| **Alternative LLM Engine** | llama.cpp (GGUF format) | Fallback for broader model compatibility |
| **Embedding Model** | LiteRT MiniLM (384-dim) | Document vectorization for RAG |
| **Vision/Image** | TensorFlow Lite / ML Kit | On-device medical image analysis |

### Data & Storage
| Component | Technology | Purpose |
|-----------|------------|---------|
| **Local DB** | Room 2.7.1+ | Chat history, metadata, embedding storage |
| **Vector Search** | Cosine similarity (Room BLOB) | Semantic document retrieval |
| **File Storage** | Internal storage + SAF | Document and model storage |
| **Document Parsing** | `pdfbox-android` | PDF text extraction |

### Download & Networking
| Component | Technology | Purpose |
|-----------|------------|---------|
| **Download Manager** | WorkManager + OkHttp | Resumable background downloads |
| **Range Requests** | HTTP Range header | Download resumption |

---

## Project Structure (Clean Architecture)

```
app/
├── src/main/java/com/yourapp/medai/
│   ├── presentation/              # UI Layer (Jetpack Compose)
│   │   ├── ui/
│   │   │   ├── chat/              # Chat screen & ViewModel
│   │   │   ├── models/            # Model management screen
│   │   │   ├── documents/         # Document upload & RAG management
│   │   │   ├── persona/           # Persona/prompt configuration
│   │   │   └── image/             # Image capture & analysis
│   │   ├── theme/                 # Material 3 theming
│   │   └── navigation/            # Compose Navigation
│   │
│   ├── domain/                    # Domain Layer (Pure Kotlin)
│   │   ├── model/                 # Business models (Chat, Document, etc.)
│   │   ├── repository/            # Repository interfaces
│   │   └── usecase/               # Use cases (business logic)
│   │
│   ├── data/                      # Data Layer
│   │   ├── repository/            # Repository implementations
│   │   ├── local/
│   │   │   ├── database/          # Room entities & DAOs
│   │   │   ├── vector/            # Vector embedding storage
│   │   │   └── dao/               # Data Access Objects
│   │   ├── model/                 # ML model management
│   │   ├── download/              # Resumable download service
│   │   └── parser/                # Document parsers (PDF, DOCX, etc.)
│   │
│   └── di/                        # Dagger Hilt Modules
│       ├── AppModule.kt
│       ├── DatabaseModule.kt
│       ├── NetworkModule.kt
│       └── ModelModule.kt
```

---

## Phase-by-Phase Development Guide

### Phase 1: Project Foundation & Architecture Setup

**Objective:** Establish the project structure, dependencies, and Clean Architecture foundation.

**Step 1.1: Initialize Project**
- Create new Android project with Empty Compose Activity
- Set `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35`

**Step 1.2: Setup Module Structure**
Create three modules in your project:
- **`:app`** - Presentation layer (UI, ViewModels, DI wiring)
- **`:domain`** - Pure Kotlin module (UseCases, Repository interfaces, Models)
- **`:data`** - Data layer (Repository impl, Room, ML inference)

**Step 1.3: Core Dependencies (`build.gradle.kts`)**

```kotlin
// Core
implementation("androidx.core:core-ktx:1.13.0")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")
implementation("androidx.activity:activity-compose:1.9.0")

// Compose
implementation(platform("androidx.compose:compose-bom:2024.10.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.ui:ui-graphics")
implementation("androidx.compose.ui:ui-tooling-preview")
implementation("androidx.compose.material3:material3")
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

// WorkManager (Downloads)
implementation("androidx.work:work-runtime-ktx:2.9.0")

// Networking (Resumable downloads)
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
```

**Reference Repositories:**
- [PocketSage](https://github.com/umarpazir11/pocketsage) - Clean Architecture with Hilt
- [offline-rag-android](https://github.com/nicolas-raoul/offline-rag-android) - RAG architecture patterns

---

### Phase 2: Resumable Model Download System

**Objective:** Implement a robust download system that survives WiFi interruptions.

**Step 2.1: Model Repository**
Create `ModelRepository` interface in domain and implementation in data layer to manage:
- Available models catalog (from bundled JSON or remote manifest)
- Download status tracking (Not Downloaded, Downloading, Paused, Completed, Error)
- Local model file paths

**Step 2.2: Resumable Download Worker**

```kotlin
class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val okHttpClient: OkHttpClient
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val modelUrl = inputData.getString(KEY_MODEL_URL) ?: return Result.failure()
        val destFile = File(applicationContext.filesDir, inputData.getString(KEY_MODEL_NAME))
        
        // Check existing partial download
        val downloadedBytes = if (destFile.exists()) destFile.length() else 0L
        
        // Request with Range header for resumption
        val request = Request.Builder()
            .url(modelUrl)
            .header("Range", "bytes=$downloadedBytes-")
            .build()
        
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return Result.retry()
            
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
        return Result.success()
    }
}
```

**Step 2.3: WorkManager Setup**

```kotlin
val downloadRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
    .setConstraints(
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
    )
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
    .setInputData(workDataOf(
        KEY_MODEL_URL to modelUrl,
        KEY_MODEL_NAME to modelName
    ))
    .build()

WorkManager.getInstance(context).enqueue(downloadRequest)
```

**Key Implementation Notes:**
- Use **HTTP Range header** for resumption
- Write to `.tmp` file first, then rename on completion to prevent corruption
- Use `setRequiredNetworkType(NetworkType.CONNECTED)` for WiFi requirement
- Use `setBackoffCriteria` for automatic retry on failure

**Reference Repository:**
- [Ketch](https://github.com/khushpanchal/Ketch) - WorkManager-based downloader with pause/resume

---

### Phase 3: On-Device LLM Integration

**Objective:** Integrate the local LLM for medical diagnosis and prescription recommendations.

**Step 3.1: Choose the Right Model**

For **Poco X7 Pro** (Dimensity 9300+, 8-12GB RAM):

| Model | Parameters | Size | Performance | Best For |
|-------|-----------|------|-------------|----------|
| **Gemma 4 E2B Instruct** | 2B | ~2.4 GB | Excellent | General medical QA, multimodal vision |
| **Qwen 2.5 1.5B Instruct** | 1.5B | ~1.5 GB | Fastest | Basic diagnosis, prescription |
| **Qwen 3 4B** | 4B | ~2.5 GB | Good | Higher accuracy, complex cases |
| **Gemma 4 E4B Instruct** | 4B | ~3.4 GB | Good | High-res multimodal |

**Recommended: Gemma 4 E2B Instruct** - Best balance of performance, accuracy, and vision capabilities for medical use.

**Step 3.2: LLM Inference Wrapper (LiteRT)**

```kotlin
class LLMInference @Inject constructor(
    private val context: Context
) {
    private var model: GenerativeModel? = null
    private var session: Session? = null
    
    suspend fun loadModel(modelPath: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Use Google AI Edge LiteRT (MediaPipe)
                model = GenerativeModel.fromFile(context, modelPath)
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
                session = model?.startSession() ?: return@withContext Result.failure(Exception("Model not loaded"))
                session?.generateStreaming(prompt) { token ->
                    // Emit token to UI
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

**Step 3.3: Defensive Programming**
- **Model Gate**: Block navigation until model is loaded
- **Session Lifecycle**: Always close sessions to prevent memory leaks
- **Error Handling**: Write human-readable errors directly to chat

**Reference Repositories:**
- [Local-LLM-AI](https://github.com/PrinceBad/Local-LLM-AI) - LiteRT integration with Jetpack Compose
- [PocketSage](https://github.com/umarpazir11/pocketsage) - Gemma 4 integration with LiteRT-LM

---

### Phase 4: Persona & Prompt Customization

**Objective:** Allow users to define custom AI personas for medical contexts.

**Step 4.1: Persona Data Model (Domain)**

```kotlin
data class Persona(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val systemPrompt: String,
    val description: String? = null,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
```

**Step 4.2: Room Entity**

```kotlin
@Entity(tableName = "personas")
data class PersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val systemPrompt: String,
    val description: String?,
    val isDefault: Boolean,
    val createdAt: Long
)
```

**Step 4.3: Prompt Construction**

```kotlin
class PromptBuilder @Inject constructor() {
    fun buildMedicalPrompt(
        persona: Persona,
        userQuery: String,
        ragContext: String? = null
    ): String {
        return buildString {
            appendLine(persona.systemPrompt)
            appendLine()
            if (!ragContext.isNullOrEmpty()) {
                appendLine("CONTEXT FROM MEDICAL DOCUMENTS:")
                appendLine(ragContext)
                appendLine()
            }
            appendLine("USER QUERY: $userQuery")
            appendLine()
            appendLine("RESPONSE (as a medical professional):")
        }
    }
}
```

**Example Medical Persona Prompt:**
```
You are Dr. AI, a compassionate and knowledgeable medical professional serving 
remote communities in 3T regions. Your role is to provide preliminary diagnosis 
and prescription recommendations based on available medical knowledge.

Guidelines:
1. Always ask clarifying questions when symptoms are unclear
2. Reference the provided medical documents for evidence
3. Recommend over-the-counter medications when appropriate
4. Clearly state when a condition requires immediate in-person care
5. Be culturally sensitive and use simple language
6. Include dosage instructions for any recommended medications
7. Never guarantee a diagnosis - always recommend professional follow-up
```

---

### Phase 5: Document Upload & RAG Pipeline

**Objective:** Enable users to upload medical documents (PDFs, Word docs) that become the knowledge base for the AI.

**Step 5.1: Document Upload with SAF**

```kotlin
class DocumentUploader @Inject constructor(
    private val context: Context
) {
    private val pickDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { handleDocument(uri) }
    }
    
    private suspend fun handleDocument(uri: Uri) {
        val fileName = getFileName(uri)
        val destFile = File(context.filesDir, "documents/$fileName")
        destFile.parentFile?.mkdirs()
        
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        // Trigger RAG processing
        processDocument(destFile)
    }
}
```

**Step 5.2: Document Parsing**

```kotlin
class DocumentParser @Inject constructor() {
    suspend fun parseDocument(file: File): String {
        return when (file.extension.lowercase()) {
            "pdf" -> parsePdf(file)
            "docx" -> parseDocx(file)
            "txt" -> file.readText()
            else -> throw UnsupportedFormatException("Format not supported")
        }
    }
    
    private suspend fun parsePdf(file: File): String {
        // Use pdfbox-android
        return withContext(Dispatchers.IO) {
            PDDocument.load(file).use { document ->
                val stripper = PDFTextStripper()
                stripper.text
            }
        }
    }
}
```

**Step 5.3: Chunking & Embedding**

```kotlin
class DocumentChunker @Inject constructor() {
    fun chunkDocument(text: String, chunkSize: Int = 512, overlap: Int = 50): List<String> {
        val words = text.split(Regex("\\s+"))
        return words.chunked(chunkSize)
            .mapIndexed { index, chunk ->
                val start = max(0, index * (chunkSize - overlap))
                words.drop(start).take(chunkSize).joinToString(" ")
            }
    }
}

class EmbeddingGenerator @Inject constructor(
    private val context: Context
) {
    private var embeddingModel: LiteRTModel? = null
    
    suspend fun generateEmbedding(text: String): FloatArray {
        // Generate 384-dimensional vector using LiteRT MiniLM
        return withContext(Dispatchers.IO) {
            embeddingModel?.run(text) ?: FloatArray(384)
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
class RagPipeline @Inject constructor(
    private val embeddingGenerator: EmbeddingGenerator,
    private val documentChunkDao: DocumentChunkDao,
    private val llmInference: LLMInference,
    private val promptBuilder: PromptBuilder,
    private val personaRepository: PersonaRepository
) {
    suspend fun query(
        userQuery: String,
        personaId: String
    ): Flow<String> = flow {
        // 1. Embed the query
        val queryEmbedding = embeddingGenerator.generateEmbedding(userQuery)
        
        // 2. Retrieve relevant documents
        val relevantChunks = documentChunkDao.findSimilarChunks(
            queryEmbedding.toByteArray(),
            topK = 5
        )
        
        // 3. Build context
        val context = relevantChunks.joinToString("\n---\n") { it.text }
        
        // 4. Get persona
        val persona = personaRepository.getPersona(personaId)
        
        // 5. Build prompt
        val prompt = promptBuilder.buildMedicalPrompt(
            persona = persona,
            userQuery = userQuery,
            ragContext = context
        )
        
        // 6. Generate response (streaming)
        llmInference.generateStreaming(prompt).collect { token ->
            emit(token)
        }
    }
}
```

**Reference Repositories:**
- [PocketSage](https://github.com/umarpazir11/pocketsage) - Complete RAG pipeline with PDF parsing, embeddings, and streaming
- [offline-rag-android](https://github.com/nicolas-raoul/offline-rag-android) - Core RAG implementation with vector similarity

---

### Phase 6: Medical Image Upload & Analysis

**Objective:** Allow users to upload photos of wounds, skin conditions, or dental issues for AI-assisted diagnosis.

**Step 6.1: Image Capture/Picker**

```kotlin
class ImageCaptureManager @Inject constructor(
    private val context: Context
) {
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { analyzeImage(it) }
    }
    
    private val captureImage = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) imageUri?.let { analyzeImage(it) }
    }
}
```

**Step 6.2: Image Analysis Options**

**Option A: Vision LLM (Recommended)**
- Use **Gemma 4 E2B Instruct** which supports multimodal vision
- Pass image directly to the model with a medical prompt

```kotlin
suspend fun analyzeWithVisionLLM(imageUri: Uri): String {
    val imageBytes = context.contentResolver.openInputStream(imageUri)?.use {
        it.readBytes()
    } ?: return "Error loading image"
    
    return llmInference.generateWithImage(
        prompt = "Analyze this medical image. Describe what you see, identify any abnormalities, and provide preliminary assessment.",
        imageData = imageBytes
    )
}
```

**Option B: Specialized TFLite Model**
- Use a lightweight CNN (EfficientNet or YOLOv8 derivative) for specific conditions
- Train on skin disease or dental condition datasets
- Convert to TensorFlow Lite for offline inference

```kotlin
class MedicalImageClassifier @Inject constructor(
    private val context: Context
) {
    private var interpreter: Interpreter? = null
    
    suspend fun loadModel() {
        val modelFile = File(context.filesDir, "models/skin_disease.tflite")
        interpreter = Interpreter(modelFile)
    }
    
    suspend fun classify(imageUri: Uri): ClassificationResult {
        val bitmap = context.contentResolver.openInputStream(imageUri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return ClassificationResult.ERROR
        
        // Preprocess and run inference
        val inputArray = preprocessImage(bitmap)
        val outputArray = Array(1) { FloatArray(NUM_CLASSES) }
        interpreter?.run(inputArray, outputArray)
        
        return ClassificationResult(
            topClass = getTopClass(outputArray[0]),
            confidence = getConfidence(outputArray[0])
        )
    }
}
```

**Step 6.3: Integration with RAG**

```kotlin
suspend fun handleImageQuery(imageUri: Uri, userQuery: String): Flow<String> = flow {
    // 1. Analyze image
    val analysis = imageAnalyzer.analyze(imageUri)
    
    // 2. Combine with user query
    val enrichedQuery = """
        USER QUERY: $userQuery
        
        IMAGE ANALYSIS:
        $analysis
        
        Based on the image analysis and user query, provide a medical assessment.
    """.trimIndent()
    
    // 3. Pass to RAG pipeline
    ragPipeline.query(enrichedQuery, personaId).collect { token ->
        emit(token)
    }
}
```

**Reference:**
- [ML Kit](https://developers.google.com/ml-kit) - Google's on-device ML SDK for image analysis
- TensorFlow Lite for custom medical models

---

### Phase 7: UI Implementation (Jetpack Compose)

**Objective:** Build a polished, responsive UI following Material 3 design.

**Step 7.1: State Management**

```kotlin
sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val ragPipeline: RagPipeline,
    private val personaRepository: PersonaRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<UiState<List<ChatMessage>>>(UiState.Idle)
    val uiState: StateFlow<UiState<List<ChatMessage>>> = _uiState.asStateFlow()
    
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()
    
    fun sendMessage(query: String, personaId: String) {
        viewModelScope.launch {
            _isStreaming.value = true
            // Add user message
            // Stream AI response
            ragPipeline.query(query, personaId).collect { token ->
                // Update streaming message
            }
            _isStreaming.value = false
        }
    }
}
```

**Step 7.2: Chat Screen**

```kotlin
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    
    Column {
        // Chat messages list
        LazyColumn(
            reverseLayout = true,
            modifier = Modifier.weight(1f)
        ) {
            items(uiState.data ?: emptyList()) { message ->
                ChatBubble(message)
            }
            if (isStreaming) {
                item { StreamingIndicator() }
            }
        }
        
        // Input area
        ChatInput(
            onSend = { viewModel.sendMessage(it, currentPersonaId) },
            onImageUpload = { viewModel.uploadImage(it) },
            enabled = !isStreaming
        )
    }
}
```

**Step 7.3: Model Management Screen**

```kotlin
@Composable
fun ModelManagementScreen(
    viewModel: ModelViewModel = hiltViewModel()
) {
    val models by viewModel.models.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    
    LazyColumn {
        items(models) { model ->
            ModelCard(
                model = model,
                progress = downloadProgress[model.id],
                onDownload = { viewModel.downloadModel(it) },
                onDelete = { viewModel.deleteModel(it) }
            )
        }
    }
}
```

**Reference Repository:**
- [Local-LLM-AI](https://github.com/PrinceBad/Local-LLM-AI) - Premium Compose UI with Material 3

---

## Best Practices for Code Quality

### SOLID Principles
- **Single Responsibility**: Each class has one reason to change (e.g., `DocumentParser` only parses, `EmbeddingGenerator` only generates embeddings)
- **Open/Closed**: Use interfaces for repositories, extend via implementation
- **Liskov Substitution**: Use sealed classes for UI states
- **Interface Segregation**: Small, focused interfaces (e.g., `IDocumentParser`, `IEmbeddingGenerator`)
- **Dependency Inversion**: Domain layer defines interfaces, data layer implements them

### DRY (Don't Repeat Yourself)
- Extract common utilities (e.g., `FileUtils`, `CoroutineUtils`)
- Use base classes for similar ViewModels
- Create reusable Compose components

### YAGNI (You Aren't Gonna Need It)
- Build only the features specified
- Don't implement generic RAG for all file types if only PDF/Word are needed
- Start with one model format (`.litertlm`) before adding GGUF support

### KISS (Keep It Simple, Stupid)
- Use simple data classes over complex inheritance
- Prefer `Flow` over `LiveData` for consistency
- Keep ViewModels focused on UI state, not business logic

### Testing Strategy
- **Unit Tests**: Test domain layer UseCases and Models
- **Instrumented Tests**: Test Room DAOs, Repository implementations
- **UI Tests**: Compose UI testing with `ComposeTestRule`

---

## Top 5 GitHub Reference Repositories

| # | Repository | Key Features | Best For |
|---|------------|--------------|----------|
| 1 | **[PocketSage](https://github.com/umarpazir11/pocketsage)** | Complete offline RAG, Clean Architecture, Hilt, LiteRT, PDF parsing, streaming LLM | **Primary reference** - Most comprehensive and well-architected |
| 2 | **[Local-LLM-AI](https://github.com/PrinceBad/Local-LLM-AI)** | LiteRT integration, premium Compose UI, model management, OCR | UI design and LiteRT integration |
| 3 | **[offline-rag-android](https://github.com/nicolas-raoul/offline-rag-android)** | Core RAG implementation, vector similarity, Clean Architecture | Understanding RAG fundamentals |
| 4 | **[Ketch](https://github.com/khushpanchal/Ketch)** | WorkManager-based resumable downloads | Download management implementation |
| 5 | **[LocalMind](https://github.com/tk85457/LocalMind)** | Full offline chatbot, llama.cpp, RAG with PDF, background downloads | Alternative LLM engine (llama.cpp) |

---

## Deployment & Performance Optimization

### For Poco X7 Pro (Target Device)
- **RAM**: 8-12 GB → Supports 2B-4B parameter models
- **CPU**: Dimensity 9300+ → Excellent for ML inference
- **GPU**: Mali-G720 → Use GPU acceleration (Vulkan)
- **Storage**: Ensure 6-8 GB free for models + documents

### Performance Tips
1. **Throttle UI updates**: Use `.sample(50)` on StateFlow to prevent recomposition overload
2. **Set thread count**: For ONNX/llama.cpp, set `intraOpNumThreads = 2` to prevent thermal throttling
3. **Model quantization**: Always use Q4_K_M or INT8 quantized models
4. **Session pooling**: Reuse LLM sessions when possible, close when done
5. **Background processing**: All ML operations on `Dispatchers.IO`

### Security & Privacy
- **Zero data leaves device** - All processing is offline
- **No API keys** - No external dependencies
- **GDPR compliant** - User data never transmitted
- **File validation**: Write to `.tmp` first, rename on completion

---

## Summary

This guide provides a complete, production-ready blueprint for building an offline medical AI assistant for Android. By following Clean Architecture principles and leveraging the specified technology stack, you can create a robust application that:

1. **Runs entirely offline** - Critical for 3T regions
2. **Processes documents locally** - RAG with PDF/Word support
3. **Downloads models resumably** - Survives WiFi interruptions
4. **Analyzes medical images** - Vision LLM or TFLite models
5. **Supports custom AI personas** - User-defined prompts
6. **Runs on Poco X7 Pro** - Optimized for target hardware

The recommended model is **Gemma 4 E2B Instruct (2B)** for its balance of performance, accuracy, and multimodal capabilities, with the **RAG pipeline** powered by LiteRT embeddings and Room-based vector storage.

Start with **PocketSage** as your primary reference, then adapt the other repositories for specific features like resumable downloads and UI polish.