# 🩺 MedBot: On-Device Medical Multi-Agent & Clinical RAG System for Android

> **100% Offline & Private On-Device AI Health Companion powered by Gemma LLM, Storage Access Framework (SAF), Deterministic Clinical Tools, and RAG Knowledge Retrieval.**

![MedBot Live On-Device Preview](./medbot_live_preview.png)

---

## 🌟 Ringkasan Fitur Utama (Core Features)

1. **🤖 Multi-Agent 46 Dokter Spesialis Medis On-Device**:
   - Dilengkapi **Triage Orchestrator** cerdas yang mengklasifikasikan keluhan pasien & modalitas citra (kulit, rontgen, lab, resep, mata, dll.) ke dokter spesialis yang paling kompeten.
   - **Query Rewriter**: Penggabungan memori percakapan multi-turn otomatis mempertahankan seluruh entitas keluhan pasien.
   - Persona AI yang dapat dipersonalisasi: 4 pilihan nada bicara (*Empathetic, Clinical, Concise, Educational*), 3 tingkat kedalaman (*Simple, Standard, Deep*), profil riwayat pasien, dan instruksi khusus.
   - Mendukung **Bahasa Indonesia 🇮🇩** dan **English 🇬🇧**.

2. **📦 Dual-Mode Model Acquisition (SAF & In-App Resumable Downloader)**:
   - **Mode 1: Folder SAF Lokal**: Memuat model Gemma (`.litertlm` / `.gguf`) langsung dari folder penyimpanan perangkat pengguna via Android Storage Access Framework tanpa perlu internet.
   - **Mode 2: In-App Resumable Downloader**: Mengunduh bundel model resmi (Gemma 4 E2B, Gemma 4 E4B, Gemma 2 2B, LiteRT-Vision) via WorkManager dan protokol HTTP Range dengan verifikasi integritas SHA-256.

3. **📚 Offline RAG Clinical Knowledge Base (SAF Document Indexer)**:
   - Parser multi-format bawaan untuk dokumen pedoman klinis Kemenkes/WHO (`.pdf`, `.txt`, `.md`).
   - Pemotongan semantik rekursif 512-token dengan overlap 50-token.
   - Vektor dense lokal 384-dimensi dan pencarian *Cosine Similarity Top-K* instan di basis data Room lokal lengkap dengan kartu sitasi halaman resmi.

4. **🧴 Skin Lineage & Visual ABCD Diagnosis**:
   - Analisis lesi kulit berdasarkan kriteria internasional **ABCD** (*Asymmetry, Border Irregularity, Color Variegation, Diameter*).
   - Penilaian tingkat risiko keganasan, diagnosis banding (Dermatitis, Eksim, Tinea/Jamur, Psoriasis, Nevus), dan rekomendasi perawatan mandiri aman.
   - Linimasa kronologis (*Skin Lineage*) terkelompok berdasarkan bagian tubuh untuk memantau evolusi lesi dari waktu ke waktu.

5. **💊 Deterministic Local Clinical Tools & Drug Encyclopedia**:
   - **Kalkulator Z-Score WHO Balita**: Deteksi status gizi (Gizi Buruk/Kurang/Baik/Lebih) dan stunting (Z-TB/U).
   - **Kalkulator Dosis Sirup Anak**: Perhitungan presisi mg/kgBB dan volume sirup (mL) untuk Paracetamol, Amoxicillin, Ibuprofen, Setirizin.
   - **Database Interaksi Obat & Alternatif Generik**: Pemeriksaan interaksi berbahaya antar-obat (Mayor, Moderat, Minor) dan obat generik terjangkau.
   - **Interpretasi Nilai Rujukan Laboratorium**: Darah Lengkap, Kimia Darah (GDS, HbA1c), Fungsi Ginjal (Kreatinin, Ureum), Fungsi Hati (SGOT/SGPT), Profil Lipid, dan Asam Urat.
   - **Jadwal Minum Obat & Pengingat Harian**.

---

## 🏛️ Arsitektur Sistem (Clean Architecture + MVVM + UDF)

```
app/src/main/java/com/medbot/app/
├── core/
│   ├── common/              # Resource wrapper, AppConstants
│   ├── datastore/           # UserPreferencesManager (DataStore/Prefs)
│   ├── designsystem/        # Material 3 Theme, Typography, Colors, Shared UI Components
│   └── di/                  # AppContainer (Service Locator & Dependency Injection)
├── data/
│   ├── ai/                  # LlmInferenceEngine, ModelRegistry
│   ├── download/            # ModelDownloadWorker (WorkManager + HTTP Range), ModelDownloadManager
│   ├── local/
│   │   ├── dao/             # ChatDao, RagDao, SkinDao, DrugDao, HealthToolsDao
│   │   ├── database/        # MedBotDatabase (Room DB: medbot_local.db)
│   │   ├── entities/        # Room Entities (Chat, Documents, Chunks, Skin, Drugs, Labs)
│   │   └── seed/            # ClinicalDataSeeder (Formularium Obat, Interaksi, Rentang Lab)
│   ├── rag/                 # DocumentParser, DocumentChunker, LocalEmbedder, VectorSearchEngine, RagOrchestrator
│   ├── repository/          # Concrete Repositories (Chat, Model, RAG, Skin, Drug, HealthTools)
│   └── vision/              # SkinDiagnosisEngine (Kaidah ABCD & Pixel-level Morphometry)
├── domain/
│   ├── agents/              # 46 Doctor Specialists Catalog, TriageOrchestrator, QueryRewriter
│   │   └── tools/           # Deterministic Local Tools (Urgency, Z-Score, Dosing, ABCD, Interactions)
│   ├── model/               # Immutable Domain Models (Chat, Persona, Skin, RAG, Health, Model)
│   ├── repository/          # Repository Interfaces
│   └── usecase/             # SendMessageUseCase, IngestSafDocumentsUseCase, AnalyzeSkinUseCase, etc.
└── presentation/
    ├── chat/                # ChatScreen & ChatViewModel (Streaming Tokens, Citations, Multi-Turn)
    ├── home/                # HomeScreen & HomeViewModel (Status Cards, Quick Triage, Modul Grid)
    ├── knowledge/           # KnowledgeBaseScreen & KnowledgeViewModel (SAF Ingestion, Vector Search)
    ├── models/              # ModelManagerScreen & ModelViewModel (Dual-Mode SAF + Downloader)
    ├── navigation/          # MedBotNavigation (Jetpack Compose Navigation Host)
    ├── persona/             # PersonaConfigScreen & PersonaViewModel (46 Specialists, Tone, Depth)
    ├── skin/                # SkinScanScreen, SkinLineageScreen & SkinViewModel (Kamera & Linimasa)
    └── tools/               # HealthToolsScreen & ToolsViewModel (Obat, Interaksi, Lab, Kalkulator, Pengingat)
```

---

## 🚀 Panduan Kompilasi & Menjalankan Aplikasi

### Persyaratan Sistem:
- **Android Studio Ladybug (2024.2+)** atau yang lebih baru.
- **JDK 17 / JDK 21**.
- **Android SDK Platform 35** (Min SDK 26, Target SDK 35).
- Perangkat Android dengan RAM minimal 4 GB (Disarankan 6-8 GB untuk akselerasi Gemma GPU/LiteRT).

### Langkah Kompilasi:
```bash
# 1. Clone repositori
git clone https://github.com/username/MedBot.git
cd MedBot

# 2. Jalankan pengujian unit lokal
./gradlew testDebugUnitTest

# 3. Bangun APK Debug
./gradlew assembleDebug
```
File APK yang dihasilkan berada di `app/build/outputs/apk/debug/app-debug.apk`.

---

## 🔒 Privasi & Keamanan Data Medis
- **Zero Cloud Leakage**: Tidak ada data percakapan, foto kulit, atau dokumen rekam medis yang dikirimkan ke server luar. Seluruh inferensi AI dan komputasi vektor berjalan 100% di dalam memori lokal ponsel pengguna.
- **Standar Klinis**: Seluruh formularium obat dan rentang referensi laboratorium disesuaikan dengan Pedoman Praktik Klinis dan Formularium Nasional.

---
© 2026 MedBot Team. Developed with Jetpack Compose & Clean Architecture.
