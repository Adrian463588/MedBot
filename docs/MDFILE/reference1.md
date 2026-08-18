Membangun aplikasi Android dengan Jetpack Compose yang menjalankan LLM medis dan RAG secara lokal sepenuhnya memungkinkan. Pendekatan ini sangat ideal untuk daerah terpencil karena semua data diproses di perangkat, menjaga privasi, dan tidak memerlukan koneksi internet setelah model diunduh.

Berikut adalah panduan lengkap dan rekomendasi repository terbaik untuk memulai.

🛠️ Rekomendasi Full Stack Teknologi

Untuk membangun aplikasi ini, Anda akan membutuhkan komponen-komponen berikut:

· Bahasa Pemrograman & UI: Kotlin dengan Jetpack Compose untuk membangun UI modern dan deklaratif.
· Arsitektur Aplikasi: MVVM (Model-View-ViewModel) dengan Android ViewModel dan Kotlin Coroutines & Flow untuk menangani state dan proses asynchronous.
· Inference Engine (LLM): Pilih salah satu opsi di bawah ini:
  · MediaPipe GenAI Tasks API: Solusi resmi dari Google, sangat baik untuk model Gemma dalam format .task.
  · llama.cpp: Engine yang sangat populer dan fleksibel, mendukung berbagai model dalam format GGUF.
  · Kotlinllamacpp: Wrapper Kotlin untuk llama.cpp yang memudahkan integrasi.
· Model LLM Medis (Format GGUF): Beberapa opsi yang sudah di-fine-tune untuk kebutuhan medis dan dapat berjalan di perangkat dengan spesifikasi terbatas:
  · SAHELI v2: Fine-tune dari Gemma 4 E4B yang dioptimalkan untuk perangkat Android kelas bawah (RAM 2GB).
  · Qwen2.5-0.5B - Saúde Amazônia: Model sangat kecil (~0.4 GB) yang sudah dilatih dengan dataset klinis.
· Vector Database (RAG): Untuk penyimpanan dan pencarian vektor secara lokal, Anda dapat menggunakan:
  · AI Edge RAG SDK: SDK resmi dari Google untuk RAG di perangkat Android.
  · llmedge: Library Android yang mendukung RAG on-device, termasuk indeks PDF dan vector search.
· Library Pendukung: Room untuk menyimpan riwayat chat secara lokal, dan OkHttp untuk mengunduh model.

📝 Panduan Langkah-demi-Langkah

1. Persiapan Proyek: Buka Android Studio, buat project baru dengan template "Empty Activity" yang menggunakan Jetpack Compose.
2. Tambahkan Dependensi: Tambahkan dependensi untuk inference engine pilihan Anda ke file build.gradle.kts. Contoh untuk MediaPipe:
   ```kotlin
   // MediaPipe GenAI Tasks API
   implementation("com.google.mediapipe:tasks-genai:0.10.14")
   ```
   Untuk llama.cpp, Anda mungkin perlu mengintegrasikannya sebagai submodule atau menggunakan library seperti kotlinllamacpp.
3. Desain UI dengan Jetpack Compose: Buat layar chat sederhana dengan LazyColumn untuk menampilkan pesan, TextField untuk input, dan Button untuk mengirim.
4. Integrasikan LLM:
   · MediaPipe: Inisialisasi LlamaInference dengan file model .task yang telah diunduh. Anda bisa mendapatkan model Gemma dari Kaggle.
   · llama.cpp: Gunakan library untuk memuat file model .gguf dari penyimpanan perangkat.
5. Implementasikan Logika Chat: Buat ViewModel yang menangani input pengguna, memanggil LLM untuk menghasilkan respons (sebaiknya dengan streaming token-by-token), dan memperbarui UI.
6. Bangun Basis Pengetahuan RAG: Kumpulkan dokumen medis (panduan, buku obat) dan ubah menjadi vektor menggunakan model embedding. Simpan vektor-vektor ini di vector database lokal seperti yang disediakan oleh AI Edge RAG SDK.
7. Integrasikan RAG ke dalam Chat: Saat pengguna bertanya, pertama lakukan pencarian vektor di database untuk menemukan dokumen yang paling relevan. Kemudian, gabungkan dokumen tersebut sebagai konteks ke dalam prompt yang dikirim ke LLM.

🏆 5 Rekomendasi Repository Top dari GitHub

Berikut adalah 5 repository terbaik yang bisa Anda gunakan sebagai referensi atau fondasi:

1. local-llm-chat (Rithik-101/local-llm-chat)
   · Deskripsi: Aplikasi chat Android offline yang sangat baik untuk memulai. Menggunakan Jetpack Compose dan MediaPipe GenAI API untuk menjalankan model lokal seperti Gemma.
   · Tech Stack: Kotlin, Jetpack Compose, MediaPipe GenAI API, Coroutines.
   · Cara Pakai: Clone repositori, buka di Android Studio, unduh file model .task dari Kaggle, dan jalankan.
2. nanoMind (vinayakkamatcodes/nanoMind)
   · Deskripsi: Aplikasi chat AI on-device yang menggunakan llama.cpp. Mendukung model GGUF, streaming real-time, dan UI modern dengan Material3.
   · Tech Stack: Kotlin, Jetpack Compose, kotlinllamacpp, MVVM.
   · Cara Pakai: Clone repositori, bangun dengan Gradle, dan letakkan file model .gguf di folder Downloads ponsel.
3. OFF-Line-AI-LLM (BEKO2210/OFF-Line-AI-LLM)
   · Deskripsi: Aplikasi Android lengkap untuk menjalankan LLM secara lokal dengan llama.cpp. Dilengkapi dengan fitur unduh model dari HuggingFace, database Room untuk riwayat chat, dan dukungan multiple model.
   · Tech Stack: Kotlin, Jetpack Compose (Material3), MVVM, Room, llama.cpp (JNI).
   · Cara Pakai: Clone repositori, inisialisasi submodule llama.cpp, dan buka di Android Studio.
4. llmedge (Aatricks/llmedge)
   · Deskripsi: Library Android yang sangat kuat untuk inference AI. Mendukung LLM (GGUF), RAG on-device (indeks PDF, vector search), STT, TTS, dan banyak lagi.
   · Tech Stack: Kotlin, llama.cpp, Whisper.cpp, dll..
   · Cara Pakai: Integrasikan sebagai dependency atau clone repositori untuk melihat contoh penggunaannya.
5. Gemma Claw (agentventure/gemmaclaw)
   · Deskripsi: Aplikasi Android yang mendemonstrasikan inference on-device dengan model Gemma menggunakan MediaPipe LLM Inference API. Dilengkapi dengan pengunduh model dan UI Compose.
   · Tech Stack: Kotlin, Jetpack Compose, MediaPipe LLM Inference API, OkHttp.
   · Cara Pakai: Clone repositori, buka di Android Studio, dan jalankan di perangkat.

💡 Proyek Menarik Lainnya (GitHub & GitLab)

· AI CareCompanion (narender-rk10/AI-CareCompanion-Offline-Health-By-Gemma): Aplikasi React Native yang mengusung konsep multi-agent untuk kesehatan, tetapi arsitektur dan ide desainnya (46 agen spesialis, routing intent) sangat menginspirasi untuk aplikasi Jetpack Compose Anda.
· Verbose (hossein-no1/verbose-ai): Aplikasi chat AI lintas platform (Android & Desktop) dengan Compose Multiplatform, menggunakan Ollama sebagai backend LLM.
· AI Healthcare Bot with Memory (extrawest/AI-Healthcare-Bot-with-Memory): Proyek dengan pendekatan backend-heavy (menggunakan LangGraph, Qdrant, Ollama) yang dapat memberi Anda wawasan tentang arsitektur RAG dan manajemen memori untuk chatbot kesehatan.

Dengan mengikuti panduan dan memanfaatkan repository di atas, Anda dapat membangun aplikasi AI助手 medis yang powerful, privat, dan dapat diandalkan untuk daerah terpencil.

Membangun aplikasi chatbot medis offline dengan RAG di Kotlin Multiplatform (KMP) menggunakan Jetpack Compose adalah proyek yang sangat ambisius namun sangat mungkin diwujudkan. Pendekatan ini ideal untuk daerah 3T karena semua data diproses di perangkat, menjaga privasi pasien, dan tidak memerlukan koneksi internet.

Berikut adalah panduan lengkap stack teknologi, langkah-langkah implementasi, dan 10 rekomendasi repository terbaik dari GitHub.

---

🏗️ Arsitektur & Stack Teknologi Lengkap

1. Model LLM Medis (On-Device)

Pilih model yang sudah di-fine-tune untuk kebutuhan medis dan tersedia dalam format GGUF (kompatibel dengan llama.cpp):

Model Base Ukuran (Q4) Keunggulan
SAHELI v2 Gemma 4 E4B (8B) ~2.5 GB FHIR + ICD-10, multi-agent triage, multimodal
MedGemma 1.5 4B Instruct Gemma 3 4B ~2.49 GB Clinical decision-support, SFT from Uganda Clinical Guidelines
Medra 4B Gemma 3 4B ~2.5 GB Clinical support, diagnostic reasoning
Medical-QA-LLM - GGUF Mobile-optimized, compatible with llama.cpp Android

Rekomendasi utama: SAHELI v2 karena sudah mendukung FHIR + ICD-10 secara native dan dioptimalkan untuk $150 Android phone.

2. Inference Engine (KMP)

Library Platform Keunggulan
Llamatik Android, iOS, Desktop, WASM True KMP, support embeddings untuk RAG, streaming, multi-session
llama-compose Android, iOS, Desktop KMP showcase dengan llama.cpp + Koog.ai agent
Koog Edge Android, iOS Bridge antara Koog Agents framework dan SLM

Rekomendasi: Llamatik karena support embeddings untuk RAG dan benar-benar multiplatform.

3. Embedding Model (untuk RAG)

Model Ukuran Keterangan
EmbeddingGemma (300M) ~300M Digunakan oleh MAM-AI untuk RAG medis
all-MiniLM-L6-v2 (INT8) ~23 MB 384 dimensi, via ONNX Runtime Mobile
GTE-small - Digunakan SAHELI untuk semantic RAG

4. Vector Database (On-Device)

Database Keunggulan
sqlite-vec Zero-dependency, SQLite extension, support hingga 50.000 chunks
FAISS Digunakan SAHELI, performa tinggi

5. Frontend (KMP + Jetpack Compose)

· Kotlin Multiplatform dengan Compose Multiplatform untuk shared UI
· Kotlin Coroutines & Flow untuk streaming token
· Koin untuk dependency injection

6. Dokumen Klinis untuk RAG

Dokumen yang perlu diindeks ke vector database:

· Panduan Praktik Klinis (PPK) tingkat pertama
· ICD-10 dan ICD-11 (kode diagnosis)
· Buku panduan Koas / dokter umum
· Farmakopae (obat-obatan)
· Pedoman WHO (11 area)

---

📝 Langkah-Langkah Implementasi

Tahap 1: Persiapan Proyek KMP

```bash
# 1. Buat project KMP dengan template "Kotlin Multiplatform App" di Android Studio
# 2. Tambahkan dependensi di build.gradle.kts (composeApp)
```

```kotlin
// build.gradle.kts (composeApp)
dependencies {
    // Compose Multiplatform
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    
    // Koin DI
    implementation("io.insert-koin:koin-core:3.5.0")
    implementation("io.insert-koin:koin-compose:1.1.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // Llamatik untuk LLM + Embeddings
    implementation("com.ferranpons.llamatik:llamatik-core:0.1.0")
    
    // SQLite untuk vector DB (Android specific)
    // implementation("androidx.sqlite:sqlite-ktx:2.4.0")
}
```

Tahap 2: Integrasi Llamatik untuk LLM

```kotlin
// Di commonMain
import com.ferranpons.llamatik.LlamaBridge
import com.ferranpons.llamatik.models.LlamaModel

class MedicalLLM {
    private val bridge = LlamaBridge()
    private lateinit var model: LlamaModel
    
    fun loadModel(modelPath: String) {
        model = bridge.loadModel(
            modelPath = modelPath,
            nCtx = 2048,
            nThreads = 2 // Batasi untuk mencegah thermal throttling[reference:31]
        )
    }
    
    fun generate(prompt: String, onToken: (String) -> Unit) {
        model.generate(
            prompt = prompt,
            streaming = true,
            onToken = { token -> onToken(token) }
        )
    }
}
```

Tahap 3: Implementasi RAG Pipeline

```kotlin
// 1. Chunking dokumen klinis
class DocumentChunker {
    fun chunk(document: String, chunkSize: Int = 512, overlap: Int = 50): List<String> {
        // Split dengan overlap untuk konteks yang lebih baik[reference:32]
        return document.chunked(chunkSize).zipWithNext { a, b ->
            a + b.take(overlap)
        }
    }
}

// 2. Embedding dengan Llamatik
class Embedder(private val bridge: LlamaBridge) {
    fun embed(text: String): FloatArray {
        return bridge.embed(text) // Llamatik support embeddings[reference:33]
    }
}

// 3. Vector Search dengan sqlite-vec
// CREATE VIRTUAL TABLE doc_embeddings USING vec0(
//     chunk_id INTEGER PRIMARY KEY,
//     embedding FLOAT[384]
// );
// SELECT chunk_id, distance FROM doc_embeddings 
// WHERE embedding MATCH ? ORDER BY distance LIMIT 5;[reference:34]
```

Tahap 4: RAG + LLM + FHIR/ICD-10

```kotlin
class MedicalRAG(
    private val llm: MedicalLLM,
    private val embedder: Embedder,
    private val vectorDb: VectorDatabase
) {
    suspend fun ask(query: String): RAGResponse {
        // 1. Embed query
        val queryEmbedding = embedder.embed(query)
        
        // 2. Retrieve relevant documents
        val relevantDocs = vectorDb.search(queryEmbedding, topK = 5)
        
        // 3. Build context
        val context = relevantDocs.joinToString("\n\n")
        
        // 4. Build prompt dengan system instruction
        val prompt = """
            Anda adalah asisten medis. Gunakan konteks berikut untuk menjawab.
            Jika konteks tidak cukup, katakan "Saya tidak memiliki informasi cukup".
            
            KONTEKS:
            $context
            
            PERTANYAAN: $query
            
            JAWABAN (dengan pertimbangan diagnosis banding, tanda bahaya, dan rujukan):
        """.trimIndent()
        
        // 5. Generate response dengan streaming
        val response = llm.generate(prompt)
        
        // 6. (Opsional) Ekstrak ICD-10 dari response[reference:35]
        val icdCodes = extractICD10(response)
        
        return RAGResponse(
            answer = response,
            retrievedDocs = relevantDocs,
            icdCodes = icdCodes
        )
    }
}
```

Tahap 5: UI dengan Jetpack Compose (Streaming)

```kotlin
@Composable
fun ChatScreen(viewModel: ChatViewModel = koinViewModel()) {
    val messages by viewModel.messages.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    
    Column {
        LazyColumn(
            modifier = Modifier.weight(1f),
            reverseLayout = true
        ) {
            items(messages) { message ->
                ChatBubble(message)
            }
            if (isStreaming) {
                item { StreamingIndicator() }
            }
        }
        
        ChatInputField(
            onSend = { text ->
                viewModel.sendMessage(text)
            }
        )
    }
}

class ChatViewModel : ViewModel() {
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()
    
    fun sendMessage(text: String) {
        viewModelScope.launch {
            _messages.update { it + Message.User(text) }
            var currentResponse = ""
            val responseMessage = Message.Assistant("")
            _messages.update { it + responseMessage }
            
            medicalRAG.ask(text).collect { token ->
                currentResponse += token
                _messages.update { messages ->
                    messages.dropLast(1) + Message.Assistant(currentResponse)
                }
            }
        }
    }
}
```

Tahap 6: Prescription dengan Huruf Cyrillic / Kode Dokter

Untuk membuat resep yang tidak terbaca oleh orang umum:

```kotlin
class PrescriptionEncoder {
    // Mapping ke huruf Cyrillic atau kode spesifik
    private val drugCodeMap = mapOf(
        "Paracetamol" to "ПАРАЦ-500",
        "Amoxicillin" to "АМОКС-250",
        // ...
    )
    
    fun encodePrescription(drugs: List<Drug>): String {
        return drugs.joinToString("\n") { drug ->
            "${drugCodeMap[drug.name] ?: drug.name} ${drug.dosage} ${drug.route}"
        }
    }
    
    fun decodePrescription(encoded: String): List<Drug> {
        // Reverse mapping
    }
}
```

---

🏆 10 Repository Terbaik dari GitHub & GitLab

Top 5 KMP + LLM

# Repository Deskripsi Tech Stack
1 DmyMi/llama-compose KMP app dengan llama.cpp inference + Koog.ai agent. Referensi terbaik untuk arsitektur KMP + LLM. KMP, Compose Multiplatform, llama.cpp, Koog.ai, Koin
2 ferranpons/Llamatik True KMP AI library: LLM, STT, Image Generation. Support embeddings untuk RAG. KMP, llama.cpp, whisper.cpp, stable-diffusion.cpp
3 lemcoder/koog-edge Bridge antara Koog Agents dan on-device LLM. Support Android & iOS. KMP, Koog, llama.cpp
4 Aban3049/DeepSeekR1 KMP project dengan local LLM (Ollama). Demonstrasi integrasi Ollama di KMP. KMP, Ollama, Jetpack Compose
5 cactus-compute/cactus-kotlin Official KMP library untuk Cactus framework. LLM + STT lokal. KMP, Cactus, iOS 12.0+, Android API 24+

Top 5 Android Native + RAG + Medis

# Repository Deskripsi Tech Stack
6 nicolas-raoul/offline-rag-android Complete offline RAG system di Android. Referensi arsitektur RAG murni. Kotlin, Jetpack Compose, Google AI Edge AICore
7 Aatricks/llmedge Library Android untuk LLM (GGUF), RAG on-device (PDF, vector search), STT, TTS. Kotlin, llama.cpp, Whisper.cpp
8 PrinceBad/Local-LLM-AI Offline Android LLM dengan Google AI Edge LiteRT + Jetpack Compose. OCR document parsing. Kotlin, Jetpack Compose, LiteRT
9 peterica/peterica-edge-rag Edge RAG architecture dengan FastAPI backend + Kotlin Jetpack Compose frontend. Kotlin, Jetpack Compose, FastAPI
10 Aatricks/llmedge-examples Comprehensive examples untuk llmedge library: LLM inference, RAG, image generation. Kotlin, llmedge, Jetpack Compose

---

🔗 Model Medis di HuggingFace

Model Link Keterangan
SAHELI v2 muthuk1/saheli-gemma4-e4b-medical FHIR + ICD-10, multi-agent triage
MedGemma 1.5 4B GGUF DuoNeural/medgemma-1.5-4b-it-LiteRT Q4_K_M, 2.49 GB
Medra 4B drwlf/Medra4b-abliterated Clinical support, diagnostic reasoning
Medical-QA-LLM saibhossain/Medical-QA-LLM Mobile-optimized GGUF

---

⚠️ Peringatan Penting

1. Bukan Pengganti Dokter: Sistem ini adalah asisten pendukung keputusan, bukan pengganti diagnosis profesional.
2. Batasi Thread: Untuk mencegah thermal throttling, batasi nThreads = 2 pada embedding dan LLM.
3. Ukuran Model: Model Q4_K_M sekitar 2.5 GB, membutuhkan RAM 6-8 GB untuk berjalan lancar.
4. Dokumen Kurasi: Kualitas RAG sangat bergantung pada kualitas dokumen yang diindeks. Gunakan sumber resmi (Kemenkes, WHO, ICD resmi).
5. ICD-10/11: SAHELI v2 sudah support HL7 FHIR R4 dengan ICD-10 codes. Untuk ICD-11, Anda perlu menambahkan dataset sendiri.

Dengan mengikuti panduan di atas dan memanfaatkan repository yang direkomendasikan, Anda dapat membangun aplikasi chatbot medis offline yang powerful, privat, dan dapat diandalkan untuk daerah terpencil.