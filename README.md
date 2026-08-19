# MedBot — On-Device Medical Multi-Agent Assistant

MedBot adalah aplikasi Android modern berbasis **Kotlin + Jetpack Compose** untuk asisten medis dan informasi kesehatan *local-first* (100% offline). Seluruh sistem triase klinis, 46 agen spesialis medis, perkakas deterministik (dosis pediatrik, interaksi obat, interpretasi laboratorium, kalkulator kehamilan & BMI), serta parser dokumen RAG berjalan sepenuhnya di perangkat tanpa ketergantungan API cloud atau telemetry eksternal.

---

## 🌟 Fitur Utama

1. **46 Agen Spesialis Medis Terpadu**:
   - Sistem triase multi-agent otomatis yang menganalisis keluhan pasien dan merutekan ke spesialis utama (`primary_specialist`) serta spesialis pendukung (`secondary_specialists`).
   - Katalog 46 spesialis meliputi Penyakit Dalam, Pediatri, Dermatologi, Kardiologi, Farmasi, Radiologi, Bedah, Psikiatri, Toksikologi, Obgyn, dll.
   - Persona yang dapat dikustomisasi (Gaya Bahasa, Nada Respons, Kedalaman Klinis, Bahasa Indonesia & English).

2. **LiteRT-LM On-Device Model Manager**:
   - Unduh langsung model AI lokal resmi dari `litert-community` di Hugging Face (Gemma 4 E2B, Qwen3 0.6B, Gemma 3 1B INT4, Gemma 4 E4B, VibeThinker-3B, LLaVA-OneVision-0.5B, InternVL3.5-1B).
   - Dukungan unduhan model kustom via URL HTTPS `.litertlm`.
   - Dukungan pemuatan model mandiri melalui Storage Access Framework (SAF) `.litertlm`.
   - Engine seleksi backend komputasi terisolasi: `AUTO`, `GPU`, atau `CPU`.
   - Manajemen unduhan lengkap: Pause, Resume, Cancel, Retry, Verifikasi SHA-256 integritas, dan Pembersihan Storage.

3. **Clinical Tools Deterministik**:
   - **Interaksi Obat**: Pemeriksaan potensi interaksi berbahaya antara dua obat atau lebih.
   - **Katalog Obat & Generik**: Pencarian obat, indikasi, dosis dewasa/anak, kontraindikasi, serta rekomendasi generik terjangkau.
   - **Evaluator Laboratorium**: Analisis parameter darah lengkap, urinalisis, profil lipid, dan fungsi organ terhadap rentang nilai rujukan terverifikasi.
   - **Kalkulator Medis**: Kalkulator Indeks Massa Tubuh (BMI), Hari Perkiraan Lahir (HPL), Dosis Pediatrik berbasis berat badan & Z-Score WHO.
   - **Pengingat Obat & Kesehatan**: Jadwal minum obat lokal dengan notifikasi sistem.

4. **Analisis Kulit & Riwayat Dermatologi (Skin Lineage)**:
   - Pengambilan foto lesi/kulit langsung via kamera atau galeri dengan validasi input lokasi anatomi tubuh.
   - Penilaian morfologi berbasis kriteria ABCD (Asymmetry, Border, Color, Diameter) secara lokal.
   - Komparator visual *Before-and-After* interaktif dengan timeline kronologis.

5. **Local Clinical Knowledge Base (RAG)**:
   - Impor dokumen medis berformat PDF, TXT, MD, dan DOCX melalui SAF dengan ekstraksi checksum SHA-256 dan provenance nyata.
   - Pencarian vektor lokal dengan skor relevansi dan penanda sitasi resmi.

---

## 🎨 Anti-Slop Design Contract & UI/UX

Aplikasi MedBot mengimplementasikan prinsip desain **Material 3 Expressive** dan **Anti-Slop Guidelines**:
- **Content-First Hierarchy**: Informasi medis disajikan secara padat, jelas, dan terstruktur tanpa elemen dekoratif sintetis yang tidak bermakna.
- **Adaptive Layout**: Responsif penuh untuk Compact Phone (<840dp) dengan *Reflow Single Pane* dan Expanded Tablet/Foldable (≥840dp) dengan *List-Detail Pane Scaffold*.
- **Microinteractions**: Interaksi mikro responsif dengan `springBounceClick`, *tactile haptic feedback*, dan target sentuh minimal 48dp.
- **Edge-to-Edge**: Pengelolaan system bar insets yang rapi di seluruh layar.
- **Honest Fail-Closed State**: Menampilkan status yang jujur dan transparan (`MODEL_UNAVAILABLE`, `EMBEDDER_UNAVAILABLE`, `VISION_UNAVAILABLE`, `INSUFFICIENT_DATA`) tanpa fabrikasi atau halusinasi data.

---

## 🏗️ Arsitektur Sistem

```text
┌────────────────────────────────────────────────────────┐
│                   Jetpack Compose UI                   │
│   (M3 Expressive + Adaptive Multi-Pane + Edge-to-Edge) │
└───────────────────────────┬────────────────────────────┘
                            │ StateFlow & Sealed UI Events
                            ▼
┌────────────────────────────────────────────────────────┐
│                    View层 (ViewModel)                  │
│       (Home, Chat, Models, Skin, Knowledge, Tools)     │
└───────────────────────────┬────────────────────────────┘
                            │ Domain Use Cases & Pure Policies
                            ▼
┌────────────────────────────────────────────────────────┐
│                   Domain Layer                         │
│  (46 Agents, Triage, ToolRegistry, Calculators, Models)│
└───────────────────────────┬────────────────────────────┘
                            │ Repository & Gateway Interfaces
                            ▼
┌────────────────────────────────────────────────────────┐
│                    Data Layer                          │
│   (Room Database, DataStore, Document Parsers,         │
│    ModelDownloadWorker / WorkManager, LiteRT-LM Engine)│
└────────────────────────────────────────────────────────┘
```

---

## 🛠️ Build & Run

### Prasyarat
- Android Studio Ladybug / Meerkat atau yang lebih baru.
- JDK 17 / 21.
- Android SDK 35 (Android 15), Minimum SDK 26 (Android 8.0).

### Perintah Gradle (PowerShell / Terminal)
```powershell
# Jalankan Unit Tests
.\gradlew.bat testDebugUnitTest

# Jalankan Linting
.\gradlew.bat lintDebug

# Build Debug APK
.\gradlew.bat assembleDebug

# Pasang di Perangkat Fisik / Emulator
.\gradlew.bat installDebug
```

---

## 📱 Live Preview & Device Verification

Aplikasi telah diverifikasi dan diuji pada perangkat fisik Android:
- **Perangkat**: Samsung SM-G988B (Galaxy S20 Ultra), Android 13 (API 33), Resolusi 1440×3200.
- **Konektivitas**: 100% Offline-capable.
- **Crash / ANR**: 0 Issues.

---

## ⚠️ Disclaimer Medis

MedBot adalah aplikasi penyedia informasi dan edukasi kesehatan. MedBot **bukan merupakan pengganti dokter, diagnosis medis profesional, atau layanan darurat**. Pada kondisi gawat darurat atau ancaman jiwa, segera hubungi layanan darurat setempat (112 / 119) atau kunjungi Instalasi Gawat Darurat (IGD) rumah sakit terdekat.

