# MedBot — On-Device Medical Multi-Agent Assistant

MedBot adalah aplikasi Android berbasis **Kotlin + Jetpack Compose** untuk informasi kesehatan *local-first*. Inference berjalan di perangkat setelah runtime dan model lokal yang tervalidasi tersedia. Download model, jika manifest resmi tersedia, adalah aksi user yang memerlukan jaringan; tidak ada cloud inference atau telemetry eksternal.

---

## 🌟 Fitur Utama

1. **46 Agen Spesialis Medis Terpadu**:
   - Sistem triase multi-agent otomatis yang menganalisis keluhan pasien dan merutekan ke spesialis utama (`primary_specialist`) serta spesialis pendukung (`secondary_specialists`).
   - Katalog 46 spesialis meliputi Penyakit Dalam, Pediatri, Dermatologi, Kardiologi, Farmasi, Radiologi, Bedah, Psikiatri, Toksikologi, Obgyn, dll.
   - Persona yang dapat dikustomisasi (Gaya Bahasa, Nada Respons, Kedalaman Klinis, Bahasa Indonesia & English).

2. **LiteRT-LM On-Device Model Manager**:
   - Import model `.litertlm` melalui Storage Access Framework (SAF).
   - Download hanya ditawarkan jika release-owned manifest memiliki URL HTTPS, ukuran exact, SHA-256, provenance, dan source revision yang sudah diverifikasi.
   - Hasil download dan partial file berada di folder SAF pilihan user, bukan `filesDir`.
   - Tidak ada custom URL model atau katalog dengan metadata buatan.
   - Engine backend tersedia sebagai `AUTO`, `GPU`, atau `CPU`.
   - Status model fail-closed: `MODEL_UNAVAILABLE`, `STORAGE_PERMISSION_REQUIRED`, `VERIFYING`, `READY_TO_LOAD`, atau `FAILED`.
   - Registry release saat ini memuat empat artefak resmi yang dipin ke revision immutable: Qwen3 0.6B, Gemma 4 E2B, LLaVA-OneVision 0.5B, dan InternVL3.5 1B. Ukuran dan SHA-256 berasal dari artefak resmi, bukan tebakan.
   - Sumber metadata: [LiteRT-LM Kotlin API](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md), [Qwen3](https://huggingface.co/litert-community/Qwen3-0.6B), [Gemma 4](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm), [LLaVA-OneVision](https://huggingface.co/litert-community/LLaVA-OneVision-0.5B), dan [InternVL3.5](https://huggingface.co/litert-community/InternVL3_5-1B).

3. **Clinical Tools Deterministik**:
   - **Interaksi Obat**: Pemeriksaan potensi interaksi berbahaya antara dua obat atau lebih.
   - **Katalog Obat & Generik**: Pencarian obat, indikasi, dosis dewasa/anak, kontraindikasi, serta rekomendasi generik terjangkau.
   - **Evaluator Laboratorium**: Analisis parameter darah lengkap, urinalisis, profil lipid, dan fungsi organ terhadap rentang nilai rujukan terverifikasi.
   - **Kalkulator Medis**: Kalkulator Indeks Massa Tubuh (BMI), Hari Perkiraan Lahir (HPL), Dosis Pediatrik berbasis berat badan & Z-Score WHO.
   - **Pengingat Obat & Kesehatan**: Jadwal minum obat lokal dengan notifikasi sistem.

4. **Skin Lineage**:
   - Pengambilan foto melalui kamera atau galeri dengan validasi input lokasi anatomi.
   - Analisis vision tetap `UNAVAILABLE` sampai model vision lokal dan output contract tervalidasi.
   - Riwayat hanya menyimpan foto dan metadata nyata yang dipilih user.

5. **Local Clinical Knowledge Base (RAG)**:
   - Impor dokumen medis berformat PDF, TXT, MD, dan DOCX melalui SAF dengan ekstraksi checksum SHA-256 dan provenance nyata.
   - Pencarian vektor lokal dengan skor relevansi dan penanda sitasi resmi.

---

## 🎨 Anti-Slop Design Contract & UI/UX

Aplikasi MedBot mengimplementasikan prinsip desain **Material 3 Expressive** dan **Anti-Slop Guidelines**:
- **Content-First Hierarchy**: Informasi medis disajikan secara padat, jelas, dan terstruktur tanpa elemen dekoratif sintetis yang tidak bermakna.
- **Adaptive Layout**: Responsif penuh untuk Compact Phone (<840dp) dengan *Reflow Single Pane* dan Expanded Tablet/Foldable (≥840dp) dengan *List-Detail Pane Scaffold*.
- **Microinteractions**: Ripple, pressed feedback, haptic terukur, loading/error/cancellation, dan target sentuh minimal 48dp.
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
│                    Presentation (ViewModel)           │
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
│   (Room Database, Preferences, Document Parsers,       │
│    ModelDownloadWorker / WorkManager, LiteRT-LM Engine)│
└────────────────────────────────────────────────────────┘
```

---

## 🛠️ Build & Run

### Prasyarat
- Android Studio Ladybug / Meerkat atau yang lebih baru.
- JDK 17.
- Android SDK 35 (Android 15), Minimum SDK 31.

### Perintah Gradle (PowerShell / Terminal)
```powershell
# Jalankan Unit Tests
.\gradlew.bat testDebugUnitTest

# Jalankan Linting
.\gradlew.bat lintDebug

# Build Debug APK
.\gradlew.bat assembleDebug

# Jalankan instrumentation pada device/emulator yang terhubung
.\gradlew.bat connectedDebugAndroidTest

# Pasang di Perangkat Fisik / Emulator
.\gradlew.bat installDebug
```

---

## 📱 Live Preview & Device Verification

Physical acceptance dijalankan terpisah dari build evidence. Setelah remediation ini, screenshot PNG hanya ditambahkan dari device yang benar-benar menjalankan APK terbaru dan melewati launch/navigation/logcat review.

Current status:
- Samsung `RRCN3008VYE` / SM-G988B, API 33: APK terbaru terpasang; launch, root navigation, large-text UI, SAF folder persistence, download nyata, pause, dan resume terverifikasi.
- Xiaomi `QSWSEMRKNFZ9LJRC`, API 35: `BLOCKED`; serial tidak tersedia pada `adb devices -l`.
- Qwen3 transfer dan load: `PASS` pada Samsung; file SAF berukuran tepat `614236160` byte, SHA-256 device cocok dengan manifest, dan LiteRT-LM menginisialisasi model lokal. Bukti ini berlaku untuk Qwen3 saja.
- LLaVA-OneVision vision: `PARTIAL_PASS`; `.part` nyata berhasil dibuat, progress berasal dari byte transfer, dan pause mempertahankan partial file. Full vision artifact dan vision runtime belum diklaim.
- Chat inference, RAG, dan skin analysis: `BLOCKED`/`UNAVAILABLE` sampai response chat, embedder, dokumen, foto, dan runtime vision nyata terverifikasi. Tidak ada fallback canned atau text-only untuk vision.

Live preview dari Samsung API 33:

- [Home — truthful local readiness](docs/e2e/preview/medbot_home_final-2026-08-19-samsung.png)
- [Model Manager — verified Qwen3 loaded from SAF](docs/e2e/preview/medbot_model_loaded_final-2026-08-19-samsung.png)
- [Medical Tools — IME keeps the action visible](docs/e2e/preview/medbot_final_tools_ime2-2026-08-19-samsung.png)
- [Vision — real download progress and pause state](docs/e2e/preview/medbot_vision_downloading-2026-08-19-samsung.png)

Acceptance matrix dan batas evidence ada di [docs/traceability.md](docs/traceability.md). PNG tidak digunakan sebagai bukti model loaded; status runtime selalu ditentukan dari byte, checksum, dan initialization nyata.

---

## ⚠️ Disclaimer Medis

MedBot adalah aplikasi penyedia informasi dan edukasi kesehatan. MedBot **bukan merupakan pengganti dokter, diagnosis medis profesional, atau layanan darurat**. Pada kondisi gawat darurat atau ancaman jiwa, segera hubungi layanan darurat setempat (112 / 119) atau kunjungi Instalasi Gawat Darurat (IGD) rumah sakit terdekat.
