# MedBot — On-Device Medical Multi-Agent Assistant

MedBot adalah aplikasi Android berbasis **Kotlin + Jetpack Compose** untuk informasi kesehatan *local-first*. Jawaban selalu dibuat oleh runtime LiteRT-LM lokal setelah model tervalidasi tersedia. Retrieval dokumen SAF/BankBook berjalan lebih dulu; evidence web resmi hanya dicari setelah opt-in saat evidence lokal tidak cukup. Tidak ada cloud inference, telemetry eksternal, atau pengiriman riwayat pasien/foto ke jaringan.

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
   - Saat engine diinisialisasi, `EngineConfig.cacheDir` diarahkan ke `context.cacheDir/litertlm-models` yang dipastikan writable. Direktori ini hanya menyimpan artefak cache LiteRT-LM yang dapat dibuat ulang untuk mempercepat load berikutnya; model canonical tetap berada di SAF dan cache dapat dihapus Android.
   - Engine backend tersedia sebagai `AUTO`, `GPU`, atau `CPU`.
   - Status model fail-closed: `MODEL_UNAVAILABLE`, `STORAGE_PERMISSION_REQUIRED`, `VERIFYING`, `READY_TO_LOAD`, atau `FAILED`.
   - Saat unduhan benar-benar berjalan, WorkManager menjalankan Worker sebagai foreground `dataSync` dan menampilkan notifikasi sistem ongoing berisi nama model, persentase, MB aktual, serta kecepatan transfer terukur. Tap notifikasi membuka Model Manager; izin `POST_NOTIFICATIONS` diminta saat pengguna menekan Unduh/Lanjutkan.
   - Registry release saat ini memuat enam artefak resmi: dua Qwen3 0.6B, Gemma 4 E2B, LLaVA-OneVision 0.5B, InternVL3.5 1B, dan MedGemma 1.5 4B IT Vision. Setiap entry memiliki URL HTTPS, ukuran, provenance, dan kontrak verifikasi; tidak ada ukuran atau checksum tebakan.
   - MedGemma memakai [endpoint resmi yang dipin ke commit artefak](https://huggingface.co/litert-community/MedGemma-1.5-4B-IT/resolve/9bcaf1a255db7a73120b1ff6baa5015512569cd2/medgemma-1.5-4b-it_q4_block32_vision_ekv2048.litertlm), berukuran `3023069488` byte. Karena sumbernya HAI-DEF gated, worker mengambil checksum LFS efektif melalui API repository terautentikasi sebelum menerima atau mempromosikan byte; SHA masked, metadata hilang, dan revision tidak cocok ditolak. Penerimaan ketentuan dan autentikasi dilakukan di Hugging Face; aplikasi tidak memiliki login akun, tetapi menerima token read-only user, menyimpannya terenkripsi dengan Android Keystore, dan membuat encrypted backup di folder SAF terpilih. Tombol unduh tetap terkunci sampai pengguna membuka sumber resmi dan mengonfirmasi penerimaan ketentuan. Setelah akses resmi diberikan, endpoint diunduh melalui worker SAF.
   - Form gated bersifat progressive disclosure: setelah token tersimpan dan source revision disetujui, input token dan checkbox tidak lagi dirender. Jika Hugging Face menolak token, input dibuka kembali; tombol `Pause`, `Resume`, `Cancel`, dan `Retry` mengikuti status transfer nyata, termasuk untuk model vision. Bearer token tidak pernah ditulis plaintext ke SAF, Room, log, atau WorkManager input. File model final, `.part`, sidecar ETag resume non-rahasia, metadata verifikasi resmi, dan envelope credential terenkripsi ditulis di folder SAF sehingga dapat direkonsiliasi setelah rebuild/reinstall; Android dapat meminta folder dipilih ulang jika grant dicabut.
   - Bila verifikasi SHA-256 gagal, worker membuang hanya `.part` dan sidecar ETag yang gagal. Tombol `Retry` kemudian memulai kandidat baru dari byte 0; byte yang sama tidak pernah dipakai ulang sebagai kandidat terverifikasi. Berkas final yang tidak cocok dipindahkan ke nama `.rejected-*` agar tidak mengunci namespace model dan tetap dapat diaudit pengguna.
   - Rebuild/update dapat memulihkan envelope credential dari SAF selama Keystore key dan SAF grant masih tersedia. Uninstall penuh dapat menghapus key tersebut; envelope tetap berada di SAF tetapi tidak dibuka dengan plaintext atau hardcoded recovery key, sehingga secure re-entry dapat tetap diperlukan. Ini tidak menghapus model yang sudah dipromosikan ke SAF; setelah re-authorization, checksum dan model dapat diverifikasi lalu dimuat kembali.
   - Saat startup, URI folder yang tersimpan tidak langsung dianggap valid: aplikasi memeriksa persisted write grant dan kemampuan directory membuat file, lalu merekonsiliasi persisted SAF tree grant berdasarkan nama artefak model atau `.part`. Pointer stale dibersihkan hanya setelah recovery gagal. Jika Android sudah mencabut grant, aplikasi meminta folder SAF dipilih ulang; berkas eksternal tidak dianggap hilang.
   - KV cache inference diterapkan melalui satu `LiteRT-LM Conversation` per sesi chat aktif. System prompt diprefill sekali, pesan lanjutan memakai conversation yang sama, request diserialisasi, dan `getTokenCount()` memicu rebuild berbasis history Room sebelum context limit tercapai. KV state hanya hidup selama proses; setelah restart, history teks terakhir diprefill ulang secara bounded.
   - Sumber metadata: [LiteRT-LM Kotlin API](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md), [Qwen3](https://huggingface.co/litert-community/Qwen3-0.6B), [Gemma 4](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm), [LLaVA-OneVision](https://huggingface.co/litert-community/LLaVA-OneVision-0.5B), [InternVL3.5](https://huggingface.co/litert-community/InternVL3_5-1B), dan [MedGemma 1.5](https://huggingface.co/litert-community/MedGemma-1.5-4B-IT).

3. **Clinical Tools Deterministik**:
   - **Interaksi Obat**: Pemeriksaan potensi interaksi berbahaya antara dua obat atau lebih.
   - **Katalog Identitas Produk**: Pencarian nama, bentuk, dan kekuatan produk. Katalog ini tidak diperlakukan sebagai monograf atau protokol terapi.
   - **Evaluator Laboratorium**: Analisis parameter darah lengkap, urinalisis, profil lipid, dan fungsi organ terhadap rentang nilai rujukan terverifikasi.
   - **Kalkulator Medis**: Kalkulator Indeks Massa Tubuh (BMI), Hari Perkiraan Lahir (HPL), Dosis Pediatrik berbasis berat badan & Z-Score WHO.
   - **Pengingat Obat & Kesehatan**: Jadwal minum obat lokal dengan notifikasi sistem.

4. **Skin Lineage**:
   - Pengambilan foto melalui kamera atau galeri dengan validasi input lokasi anatomi.
   - Analisis vision tetap `UNAVAILABLE` sampai model vision lokal dan output contract tervalidasi.
   - Riwayat hanya menyimpan foto dan metadata nyata yang dipilih user.

5. **Local Clinical Knowledge Base (RAG)**:
   - Corpus BankBook `DataCleaned/rag_chunks_medgemma.jsonl` yang bersumber dari data cleaned nyata disertakan sebagai asset terverifikasi, lalu di-parse dan di-embed secara lokal ke tabel Room `rag_documents`/`doc_chunks` saat aplikasi berjalan.
   - Asset BankBook versi `2.2.0` memiliki 1.132 record, ukuran `2642327` byte, dan SHA-256 `08dc04293e6e4b36e811b64cd3a0ac165962ea484d16799d97e530a4410b629a` (snapshot `2026-08-25`). Record Diare dipulihkan dari source text agar bagian anamnesis, penatalaksanaan, batasan antibiotik/antidiare, dan tanda bahaya tetap masuk; synopsis WHO 2024, index 144 diagnosis SKDI/4A, status regulasi JDIH 2026, serta cuplikan edukasi Halodoc/K24 berprovenance ditambahkan. Index 144 hanya classification-only; web hanya education-only dan tidak dapat menjadi dasar resep. Indexing idempoten; jawaban belum memakai corpus sebelum embedding selesai. Saat seed pertama masih berjalan, chat menunggu state index secara bounded dan UI menampilkan state indexing.
   - Dokumen tambahan tetap dapat diimpor melalui SAF dengan ekstraksi checksum SHA-256 dan provenance nyata. Pencarian memakai embedding MiniLM TFLite terverifikasi dan lexical overlap dari teks sumber sebagai penguat exact-term; sitasi hanya berasal dari metadata sumber.
   - Retrieval klinis mengambil query asli dan query kedua yang menargetkan section anamnesis/triase/penatalaksanaan atau monograf/indikasi, menggabungkan kandidat berdasarkan chunk dan skor terukur, meranking judul topik (`Diare`) serta section klinis, lalu membuang chunk yang hanya menyebut gejala secara kebetulan. Pertanyaan klinis diwajibkan melalui kontrak triase → probing → arah diagnosis banding → fakta terapi/obat berbasis sumber → tanda bahaya. Tidak ada diagnosis final, resep individual, atau formula racikan tanpa protokol eksplisit.
   - `docs/dataset/repair_clinical_corpus.py` dan `docs/dataset/scrape_clinical_references.py` adalah jalur intake build-time yang dapat diulang. APK tidak melakukan cloud inference atau scraping arbitrer. Jika evidence lokal kurang, pengguna dapat mengaktifkan fallback evidence web resmi dari Chat atau mengimpor dokumen nyata melalui SAF untuk corpus offline dengan checksum/provenance.
   - Fallback web bersifat evidence-only: query saat ini disanitasi, history/foto/profil tidak dikirim, sumber dibatasi ke HTTPS WHO dan NCBI/PubMed, `robots.txt` dipatuhi, request dan response dibatasi, hasil hanya di-cache in-memory dengan TTL, dan citation menyimpan URL/source role untuk dibuka pengguna. Network/offline/source failure menghasilkan `UNAVAILABLE`, bukan jawaban sintetis.
   - Rujukan implementasi: [Android network state](https://developer.android.com/develop/connectivity/network-ops/reading-network-state), [RFC 9309 robots policy](https://www.rfc-editor.org/rfc/rfc9309.html), [NCBI E-utilities](https://www.ncbi.nlm.nih.gov/books/NBK25499/), dan [WHO diarrhoea guidance](https://www.who.int/health-topics/diarrhoea).

### Pipeline Chatbuddy

Setiap pertanyaan klinis melewati pipeline typed berikut:

```text
input validation -> triage/red flags -> dynamic probing
-> local BankBook/SAF RAG -> optional allowlisted web evidence
-> LiteRT-LM local inference -> citation/structure guardrail -> Room
```

Chatbuddy tidak menegakkan diagnosis final atau menerbitkan resep individual. Fakta obat hanya dapat diringkas dari evidence bertipe `GUIDELINE`, `DRUG_MONOGRAPH`, `DRUG_INTERACTION`, atau `COMPOUNDING_PROTOCOL` yang memiliki provenance; katalog nama produk tidak memenuhi gate. Bila evidence, embedder, model, atau runtime vision tidak tersedia, aplikasi menampilkan state fail-closed dan tidak menyimpan jawaban buatan. Web fallback default-nya mati, harus diaktifkan user, hanya mengirim topik pertanyaan saat ini yang telah disanitasi ke WHO/NCBI/PubMed, dan citation-nya dapat dibuka dari UI.

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
- Samsung `RRCN3008VYE` / SM-G988B, API 33: APK debug terbaru berhasil di-install ulang dengan `adb install -r -d` tanpa menghapus data, package diaktifkan kembali untuk user 0, launcher resolved, dan `com.medbot.app/.MainActivity` resumed tanpa crash/ANR. `connectedDebugAndroidTest` lulus 7/7, termasuk retrieval real MiniLM TFLite + Room + BankBook dan query medication-eligible. Generated clinical response belum diklaim karena tidak ada LiteRT-LM model yang tervalidasi dan loaded pada physical run ini.
- Xiaomi `QSWSEMRKNFZ9LJRC`, API 35: `BLOCKED (INSTALL_FAILED_USER_RESTRICTED)`; device terlihat tetapi policy perangkat menolak instalasi APK/instrumentation.
- Qwen3 transfer dan load: evidence historis `PASS` tetap model-specific; physical Chatbuddy generation pada run 2026-08-27 belum diklaim tanpa model loaded. Model canonical tetap wajib berada di SAF dan tidak dipulihkan dari cache private.
- LLaVA-OneVision vision: `PARTIAL_PASS`; `.part` nyata berhasil dibuat, progress berasal dari byte transfer, dan pause mempertahankan partial file. Full vision artifact dan vision runtime belum diklaim.
   - Chat inference dan MedGemma vision tetap `BLOCKED`/`UNAVAILABLE` karena device run ini tidak memiliki model lokal loaded/SAF destination dan tidak memiliki runtime vision yang tervalidasi. Tidak ada fallback canned atau text-only untuk vision.

Live preview dari Samsung API 33:

- [Home — runtime launch 2026-08-21](docs/e2e/preview/medbot_home_runtime-2026-08-21-samsung.png)
- [MedGemma — gated access, token, and terms confirmation](docs/e2e/preview/medbot_medgemma_access_bottom_runtime-2026-08-21-samsung.png)
- [Home — truthful local readiness](docs/e2e/preview/medbot_home_final-2026-08-19-samsung.png)
- [Model Manager — verified Qwen3 loaded from SAF](docs/e2e/preview/medbot_model_loaded_final-2026-08-19-samsung.png)
- [Medical Tools — IME keeps the action visible](docs/e2e/preview/medbot_final_tools_ime2-2026-08-19-samsung.png)
- [Vision — real download progress and pause state](docs/e2e/preview/medbot_vision_downloading-2026-08-19-samsung.png)
- [MedGemma — fail-closed gated access and SAF-required state](docs/e2e/preview/medbot_model_manager_gated_unavailable-2026-08-22-samsung.png)
- [Home — current Chatbuddy physical preview, local-readiness state](docs/e2e/preview/medbot_home_chatbuddy-2026-08-27-samsung.png)

Acceptance matrix dan batas evidence ada di [docs/traceability.md](docs/traceability.md). JSON device report terbaru ada di [medbot-chatbuddy-2026-08-27.json](docs/e2e/reports/medbot-chatbuddy-2026-08-27.json). PNG tidak digunakan sebagai bukti model loaded; status runtime selalu ditentukan dari byte, checksum, dan initialization nyata.

---

## ⚠️ Disclaimer Medis

MedBot adalah aplikasi penyedia informasi dan edukasi kesehatan. MedBot **bukan merupakan pengganti dokter, diagnosis medis profesional, atau layanan darurat**. Pada kondisi gawat darurat atau ancaman jiwa, segera hubungi layanan darurat setempat (112 / 119) atau kunjungi Instalasi Gawat Darurat (IGD) rumah sakit terdekat.
