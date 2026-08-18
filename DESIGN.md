# DESIGN.md — System Architecture & UI/UX Design System
## MedBot: On-Device Medical Assistant (Jetpack Compose)

Dokumen ini menjelaskan arsitektur perangkat lunak, rancangan data flow, integrasi Storage Access Framework (SAF), arsitektur *In-App Resumable Model Downloader*, pipeline RAG lokal, sistem diagnosis visual *Skin Lineage*, skema basis data Room, serta sistem desain UI/UX berbasis Jetpack Compose & Material 3.

---

## 1. Arsitektur Perangkat Lunak (Clean Architecture + UDF)

MedBot mengimplementasikan prinsip **Clean Architecture** yang dipadukan dengan pola **Unidirectional Data Flow (UDF)** dan **MVVM** untuk memastikan pemisahan tanggung jawab (*separation of concerns*), kemudahan pengujian (*testability*), dan keandalan tinggi saat beroperasi offline.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           PRESENTATION LAYER                                │
│  Jetpack Compose UI  │  ViewModels  │  Navigation Graph  │  UI State (M3)   │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ (Observes UI State / Sends Intents)
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              DOMAIN LAYER                                   │
│  UseCases / Interactors  │  Domain Models (POJO)  │  Repository Interfaces  │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ (Implements Interfaces)
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                               DATA LAYER                                    │
│ ┌──────────────────────┐ ┌───────────────────────┐ ┌──────────────────────┐ │
│ │    AI & Inference    │ │   Knowledge & RAG     │ │   Local Persistence  │ │
│ │  - LiteRT-LM Engine  │ │  - SAF Document Parser│ │  - Room Database     │ │
│ │  - llama.cpp Wrapper │ │  - Local Embedder     │ │  - DataStore Prefs   │ │
│ │  - Vision Processor  │ │  - Vector Store       │ │  - File Sandbox      │ │
│ │  - Model Downloader  │ │                       │ │  - SAF Repositories  │ │
│ └──────────────────────┘ └───────────────────────┘ └──────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.1 Struktur Paket & Modul Proyek
```
com.medbot.app/
├── core/
│   ├── common/             # Extension functions, Result wrapper, Constants
│   ├── database/           # Room Database, TypeConverters, Migration
│   ├── datastore/          # DataStore Preferences (Persona, Theme, SAF URIs)
│   ├── designsystem/       # Material 3 Theme, Typography, Color, Components
│   └── di/                 # Dagger Hilt Modules (AppModule, AiModule, DatabaseModule)
├── data/
│   ├── ai/                 # LiteRT-LM / llama.cpp engines, ModelLoader
│   ├── download/           # WorkManager ResumableDownloadWorker, OkHttp Range Client
│   ├── rag/                # SAF DocumentImporter, PDF Parser, Chunker, Embedder
│   ├── vision/             # CameraX helper, ABCD Skin Evaluator, Vision Engine
│   ├── repository/         # Implementasi repository (Chat, Model, RAG, Skin, Drug)
│   └── local/              # Room DAOs & Entities
├── domain/
│   ├── model/              # Domain entities (ChatMessage, Agent, SkinRecord, DocChunk)
│   ├── repository/         # Interface kontrak repository
│   └── usecase/            # Business logic (SendMessageUseCase, IngestDocsUseCase, etc.)
└── presentation/
    ├── navigation/         # NavHost, Screen Routes, BottomBar Navigation
    ├── home/               # HomeScreen, DashboardViewModel
    ├── chat/               # ChatScreen, ChatViewModel, Bubble Components
    ├── skin/               # SkinDiagnosisScreen, SkinLineageScreen, CameraView
    ├── knowledge/          # KnowledgeBaseScreen, DocumentIndexingViewModel
    ├── models/             # ModelManagerScreen (Download & SAF Tabs), ModelViewModel
    ├── persona/            # PersonaConfigScreen, PersonaViewModel
    └── tools/              # DrugInfoScreen, LabInterpreterScreen, ReminderScreen
```

---

## 2. Model Acquisition & Storage Architecture

MedBot mendukung strategi perolehan model ganda (*Dual-Mode Model Acquisition*):
1. **Pemuatan Berkas Lokal via Storage Access Framework (SAF)**
2. **Pengunduhan Model Resumable Langsung di Aplikasi (In-App Downloader)**

```
                               ┌─────────────────────────────┐
                               │   Pilihan Akuisisi Model    │
                               └──────────────┬──────────────┘
                                              │
                      ┌───────────────────────┴───────────────────────┐
                      ▼                                               ▼
        ┌───────────────────────────┐                   ┌───────────────────────────┐
        │  1. Pemuatan Folder SAF   │                   │  2. In-App Model Download │
        │ (ACTION_OPEN_DOCUMENT_TREE│                   │  (Tombol Unduh di App)    │
        └─────────────┬─────────────┘                   └─────────────┬─────────────┘
                      │                                               │
                      ▼                                               ▼
        ┌───────────────────────────┐                   ┌───────────────────────────┐
        │ Simpan Persistable URI    │                   │ WorkManager Background Job│
        │ Permission                │                   │ (OkHttp HTTP Range Header)│
        └─────────────┬─────────────┘                   └─────────────┬─────────────┘
                      │                                               │
                      ▼                                               ▼
        ┌───────────────────────────┐                   ┌───────────────────────────┐
        │ Scan Model (.litertlm/    │                   │ Unduh ke .part -> Validasi│
        │ .gguf) di Folder SAF      │                   │ Checksum SHA-256          │
        └─────────────┬─────────────┘                   └─────────────┬─────────────┘
                      │                                               │
                      └───────────────────────┬───────────────────────┘
                                              │
                                              ▼
                                ┌───────────────────────────┐
                                │ Muat Model ke RAM Engine  │
                                │ (LiteRT-LM / llama.cpp)   │
                                └───────────────────────────┘
```

### 2.1 Manajemen Model LLM via SAF
1. **Pemilihan Folder**: Pengguna menunjuk folder di penyimpanan internal/SD card yang berisi berkas model (contoh: `gemma-4-e2b.litertlm` atau `gemma-2-2b.gguf`).
2. **Pemberian Izin Persisten**:
   ```kotlin
   val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
   context.contentResolver.takePersistableUriPermission(folderUri, takeFlags)
   ```
3. **Pemuatan Berkas**: Engine C++/Native membaca berkas melalui *File Descriptor* (`ParcelFileDescriptor.open`) atau menyalin berkas ke cache *app-private* terenkripsi untuk inferensi dengan latensi terendah.

### 2.2 Arsitektur In-App Resumable Model Downloader (Online On-Demand)
Untuk pengguna yang belum memiliki model di penyimpanan lokal, aplikasi menyediakan tombol unduh langsung (*one-tap download*) dengan arsitektur unduhan latar belakang yang tangguh:

```
[ Pengguna Menekan Tombol "Unduh" di Model Manager ]
                        │
                        ▼
           [ ModelDownloadCoordinator ] ──► Validasi ruang penyimpanan bebas (> 2x ukuran model)
                        │
                        ▼
           [ Android WorkManager ] ──► Menjadwalkan ResumableDownloadWorker (Constraints: Wi-Fi/Unmetered)
                        │
                        ▼
           [ OkHttp Download Client ] ──► Mengirim HTTP Range Header ("Range: bytes=X-", "If-Range: etag")
                        │
                        ▼
           [ Stream ke .part File ] ──► /files/models/downloads/gemma-4-e2b.litertlm.part
                        │
                        ▼
           [ Verifikasi Checksum SHA-256 ] ──► Jika cocok, Rename Atomik -> gemma-4-e2b.litertlm
                        │
                        ▼
           [ Update Status Model ] ──► READY_TO_LOAD / INSTALLED di Room DB & DataStore
```

#### Struktur Manifest Model:
```kotlin
data class ModelManifest(
    val id: String,
    val displayName: String,
    val version: String,
    val format: ModelFormat, // LITERTLM, GGUF, ONNX
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
    val minimumRamMb: Int,
    val isMultimodal: Boolean,
    val recommendedBackend: String // "GPU", "CPU", "AUTO"
)

enum class ModelDownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    READY_TO_LOAD,
    LOADED_IN_RAM,
    ERROR
}
```

---

## 3. On-Device RAG & Vector Search Pipeline

Pipeline RAG memungkinkan MedBot menjawab pertanyaan berbasis dokumen lokal (seperti Panduan Praktik Klinis Dokter, Buku Saku Obat, Panduan Kemenkes/WHO) tanpa koneksi internet.

```
[ SAF Medical Document (PDF/TXT/MD) ]
                │
                ▼
      [ Document Parser ] ──► (pdfbox-android / PlainTextParser)
                │
                ▼
      [ Recursive Chunker ] ──► (512 token / chunk, overlap 50 token)
                │
                ▼
     [ On-Device Embedder ] ──► (Gecko 110M / all-MiniLM-L6-v2 ONNX)
                │
                ▼
      [ Room SQLite DB ] ──► (Table: doc_chunks dengan FLOAT/BLOB vector)
```

```
[ User Medical Query ] ──► [ Embed Query ]
                                  │
                                  ▼
                     [ Cosine Similarity Search ] ◄── [ Room DB ]
                                  │
                                  ▼
                     [ Top-K Context Selection (3-5 Chunks) ]
                                  │
                                  ▼
                     [ Augmented Prompt Generation ]
                                  │
                                  ▼
                   [ Gemma LLM On-Device Inference ]
                                  │
                                  ▼
                   [ Streaming Answer + Source Citations ]
```

### 3.1 Komputasi Vektor & Cosine Similarity
Pencarian kesamaan semantik menggunakan rumus *Cosine Similarity*:

$$\text{Cosine Similarity}(A, B) = \frac{A \cdot B}{\|A\| \|B\|} = \frac{\sum_{i=1}^n A_i B_i}{\sqrt{\sum_{i=1}^n A_i^2} \sqrt{\sum_{i=1}^n B_i^2}}$$

Pencarian dilakukan secara cepat menggunakan loop teroptimasi Kotlin / SIMD pada array float di memori, mendukung hingga puluhan ribu potongan dokumen (*chunks*) dengan waktu respons < 15ms.

---

## 4. Pipeline Diagnosis Kulit & "Skin Lineage" (Linimasa Visual)

Fitur Skin Lineage memberikan kemampuan diagnostik visual dan pemantauan perkembangan kondisi kulit pasien dari waktu ke waktu.

```
┌────────────────────────┐
│  CameraX Capture /     │
│  Photo Picker (SAF)    │
└───────────┬────────────┘
            │
            ▼
┌────────────────────────┐
│ Preprocessing & Crop   │ ──► Penyimpanan lokal di app-private storage:
│ (Normalisasi Citra)    │     /data/user/0/com.medbot.app/files/skin_images/
└───────────┬────────────┘
            │
            ▼
┌────────────────────────┐
│ On-Device Vision Model │ ──► Multimodal Gemma / LiteRT Vision Model
│ (Dermatology Analysis) │
└───────────┬────────────┘
            │
            ├──────────────────────────────────────────────┐
            ▼                                              ▼
┌───────────────────────────────┐              ┌───────────────────────────────┐
│   Evaluasi Kaidah ABCD        │              │  Klasifikasi Indikasi Awal    │
│  - Asymmetry (A)              │              │  (Eksim, Psoriasis, Tinea,    │
│  - Border Irregularity (B)    │              │   Dermatitis Kontak, dll.)    │
│  - Color Variation (C)        │              │  + Penentuan Skor Urgensi     │
│  - Diameter Estimation (D)    │              └───────────────┬───────────────┘
└───────────────┬───────────────┘                              │
                │                                              │
                └──────────────────────┬───────────────────────┘
                                       │
                                       ▼
                       ┌───────────────────────────────┐
                       │  Simpan Entitas SkinRecord    │
                       │     ke Basis Data Room        │
                       └───────────────┬───────────────┘
                                       │
                                       ▼
                       ┌───────────────────────────────┐
                       │  Tampilan Skin Lineage UI     │
                       │ (Linimasa + Slider Komparasi) │
                       └───────────────────────────────┘
```

### 4.1 Skema Data Pelacakan Skin Lineage
Setiap rekaman foto kulit dicatat dengan metadata lokasi tubuh, tanggal, skor ABCD, dan catatan gejala:
* **Lokasi Tubuh**: Wajah, Leher, Lengan Kanan, Lengan Kiri, Punggung, Dada, Kaki, dll.
* **Komparasi Visual**: Fitur *Interactive Split Slider* yang memungkinkan pasien membandingkan foto hari ke-1 dengan foto hari ke-7 untuk melihat apakah kemerahan/luas lesi berkurang.

---

## 5. Skema Basis Data Room (Local SQLite)

Aplikasi beroperasi sepenuhnya tanpa server autentikasi. Seluruh data disimpan dalam Room Database lokal bernama `medbot_local.db`.

```
┌───────────────────────┐          ┌───────────────────────┐
│     chat_sessions     │ 1      * │     chat_messages     │
├───────────────────────┤──────────├───────────────────────┤
│ id: String (PK)       │          │ id: String (PK)       │
│ title: String         │          │ sessionId: String(FK) │
│ agentId: String       │          │ text: String          │
│ createdAt: Long       │          │ isUser: Boolean       │
│ updatedAt: Long       │          │ agentId: String       │
└───────────────────────┘          │ citationsJson: String │
                                   │ createdAt: Long       │
                                   └───────────────────────┘

┌───────────────────────┐          ┌───────────────────────┐
│     rag_documents     │ 1      * │      doc_chunks       │
├───────────────────────┤──────────├───────────────────────┤
│ id: String (PK)       │          │ id: String (PK)       │
│ fileName: String      │          │ docId: String (FK)    │
│ fileUri: String       │          │ chunkIndex: Int       │
│ pageCount: Int        │          │ textContent: String   │
│ chunkCount: Int       │          │ pageNumber: Int       │
│ sha256: String        │          │ sectionTitle: String  │
│ indexedAt: Long       │          │ embeddingBlob: BLOB   │
└───────────────────────┘          └───────────────────────┘

┌───────────────────────┐          ┌───────────────────────┐
│     skin_records      │          │       drugs_db        │
├───────────────────────┤          ├───────────────────────┤
│ id: String (PK)       │          │ name: String (PK)     │
│ bodyPart: String      │          │ genericName: String   │
│ imagePath: String     │          │ indication: String    │
│ asymmetryScore: Float │          │ adultDose: String     │
│ borderScore: Float    │          │ childDose: String     │
│ colorScore: Float     │          │ contraindications: Str│
│ diameterMm: Float     │          │ sideEffects: String   │
│ differentialDx: String│          │ isOtc: Boolean        │
│ urgencyLevel: String  │          └───────────────────────┘
│ userNotes: String     │
│ createdAt: Long       │
└───────────────────────┘
```

---

## 6. UI/UX Design System (Material 3 & Jetpack Compose)

Sistem antarmuka dirancang dengan estetika modern, ramah pengguna, berorientasi medis yang menenangkan, serta mendukung mode Gelap dan Terang secara dinamis.

### 6.1 Palet Warna Klinis (Color Palette)
* **Primary (Deep Medical Emerald)**: `#0D7C66` (Memberikan rasa aman, tenang, dan profesional)
* **Primary Container (Soft Mint)**: `#D1F2EB` (Latar belakang elemen penting yang nyaman di mata)
* **Secondary (Teal Accent)**: `#1DB589` (Aksen tombol interaktif dan badge spesialis)
* **Background Light**: `#F8FBFB` | **Background Dark**: `#101817`
* **Surface Light**: `#FFFFFF` | **Surface Dark**: `#182422`
* **Urgency Alerts**:
  - *Emergency / Red Flag*: `#E74C3C` (Merah Darurat)
  - *Warning / Need Attention*: `#F39C12` (Kuning Perhatian)
  - *Normal / Safe*: `#27AE60` (Hijau Aman)

### 6.2 Tipografi (Typography Scale)
Menggunakan keluarga huruf modern (Inter / Roboto) dengan hierarki yang jelas:
* `HeadlineLarge` (24sp Bold): Judul layar utama & nama pasien
* `TitleMedium` (16sp SemiBold): Judul kartu modul & nama spesialis
* `BodyLarge` (15sp Regular): Teks gelembung percakapan medis
* `LabelSmall` (11sp Medium): Lencana status model, sitasi halaman, timestamp

### 6.3 Desain & Alur Layar Utama (Screen Flow)

```
                            ┌─────────────────────┐
                            │     Splash Screen   │
                            └──────────┬──────────┘
                                       │
                                       ▼
                            ┌─────────────────────┐
                            │     Main Navigation │
                            │      Bottom Bar     │
                            └──────────┬──────────┘
           ┌─────────────────┬─────────┴─────────┬─────────────────┐
           ▼                 ▼                   ▼                 ▼
   ┌───────────────┐ ┌───────────────┐   ┌───────────────┐ ┌───────────────┐
   │  HomeScreen   │ │  ChatScreen   │   │  SkinLineage  │ │  ToolsScreen  │
   └───────┬───────┘ └───────┬───────┘   └───────┬───────┘ └───────┬───────┘
           │                 │                   │                 │
    [Status Model &   [Streaming Token,   [Camera Capture,  [Drug Search,
     RAG Card, Quick   Markdown, Source    ABCD Evaluation,  Lab Test,
     Triage, Persona]  Pills, Citations]   Timeline Slider]  Reminders]
           │
           ├───────────────────────────────┐
           ▼                               ▼
   ┌───────────────────────┐       ┌───────────────┐
   │     ModelManager      │       │ KnowledgeBase │
   │ (Downloader & SAF Tab)│       │ (SAF RAG Ingest│
   └───────────────────────┘       └───────────────┘
```

#### Komponen Utama UI:
1. **Status Banner Card**: Menampilkan status engine model AI (`Siap Digunakan • Gemma 4 E2B`, `Model Belum Dimuat`, atau `Sedang Inisialisasi`) beserta jumlah dokumen RAG yang terindeks.
2. **Model Manager Screen (Dual-Tab)**:
   - **Tab 1 — Unduh Model Online**: Menampilkan kartu model resmi (Gemma 4 E2B, Gemma 4 E4B, Gemma 2 2B, LiteRT Vision Bundle), tombol **Unduh** / **Jeda** / **Lanjut**, progress bar persentase unduhan, kecepatan unduh, dan tombol **Muat ke RAM**.
   - **Tab 2 — Folder Lokal (SAF)**: Tombol pemilih folder perangkat (`ACTION_OPEN_DOCUMENT_TREE`), daftar berkas model lokal yang terdeteksi, dan tombol pemilihan model aktif.
3. **Specialist Badge & Citation Chip**: Di setiap pesan AI, terdapat chip nama dokter spesialis (contoh: `🩺 Dr. Spesialis Kulit`) dan chip kutipan dokumen yang dapat diklik untuk membuka modal pembaca cuplikan sumber asli.
4. **Interactive Skin Comparator Slider**: Komponen geser vertikal/horizontal yang membagi dua foto kulit untuk melihat perubahan lesi secara presisi.

---

## 7. Penanganan Kinerja, Memori & Termal

| Masalah Potensial | Solusi Arsitektural yang Diterapkan |
| :--- | :--- |
| **Peningkatan Suhu (Thermal Throttling)** | Membatasi alokasi *inference threads* menjadi maksimal 2–3 core dan menjalankan proses RAG secara asinkron di latar belakang (*background dispatchers*). |
| **Out-of-Memory (OOM) pada Model 4B** | Memeriksa ketersediaan RAM perangkat (`ActivityManager.MemoryInfo`). Jika RAM bebas < 1.5 GB, aplikasi menampilkan rekomendasi penggunaan model 2B atau menurunkan panjang *context window*. |
| **UI Recomposition Lag saat Streaming** | Menggunakan operator Flow `.sample(50.milliseconds)` pada emisi token untuk memperbarui UI setiap 50ms alih-alih pada setiap karakter tunggal. |
| **Perubahan Lokasi File Asli SAF** | Setiap dokumen yang diimpor dari SAF langsung diproses dan diekstrak teksnya ke Room DB pada saat pertama kali dipilih, sehingga RAG tidak rusak jika file asli dipindahkan. |
