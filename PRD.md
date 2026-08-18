# Product Requirements Document (PRD)
## MedBot — On-Device Medical AI Assistant & Health Companion

| Dokumen | Product Requirements Document (PRD) |
| :--- | :--- |
| **Proyek** | MedBot (Android Jetpack Compose) |
| **Versi** | 1.0.0 |
| **Status** | Approved / Ready for Implementation |
| **Target OS** | Android 8.0+ (API 26) s.d. Android 15 (API 35) |
| **Bahasa Utama** | Bahasa Indonesia (Default) & English |
| **Konektivitas** | Offline-First (Inferensi AI 100% Lokal/On-Device; Internet hanya digunakan opsional untuk unduh bundle model AI) |

---

## 1. Executive Summary & Visi Produk

### 1.1 Latar Belakang
Akses terhadap layanan kesehatan berkualitas dan konsultasi medis cepat sering kali terbatas di wilayah terpencil, kepulauan, dan pedesaan (wilayah 3T). Di sisi lain, privasi data kesehatan merupakan isu krusial di mana pengguna enggan membagikan riwayat penyakit pribadi dan foto kondisi medis ke server cloud pihak ketiga.

**MedBot** adalah aplikasi Android berbasis **Jetpack Compose** yang berfungsi sebagai asisten medis cerdas, privat, dan beroperasi **100% secara offline di perangkat (on-device)**. Menggunakan model bahasa lokal (Gemma) dan model visi medis, MedBot mampu melakukan triase gejala, menjawab pertanyaan medis berbasis dokumen resmi (RAG), memberikan konsultasi dari 46 spesialis medis, serta mendiagnosis dan melacak perkembangan kondisi kulit (*Skin Lineage*).

### 1.2 Pilar Utama Produk
1. **Privasi Mutlak & 100% Offline Inference**: Tidak ada data medis, teks chat, atau citra foto yang keluar dari perangkat. Seluruh inferensi teks dan visi berjalan di perangkat.
2. **Fleksibilitas Pemuatan & Pengunduhan Model AI (Dual-Mode Acquisition)**:
   - **Pemuatan Berkas Berbasis SAF**: Memuat berkas model lokal (`.litertlm`, `.gguf`) dan kumpulan dokumen RAG dari folder mana pun di perangkat.
   - **In-App Resumable Model Downloader**: Tombol unduh langsung di aplikasi untuk mengunduh bundle model AI LLM/Vision dari internet (HuggingFace/CDN resmi) dengan fitur jeda/lanjut otomatis (*pause/resume*).
3. **Multi-Agent & Persona Adaptif**: Sistem cerdas yang mengorkestrasi 46 agen spesialis medis dengan gaya komunikasi (persona) yang dapat disesuaikan pengguna.
4. **Dermatologi Visual & Skin Lineage**: Kemampuan memotret lesi/ruam kulit, mendiagnosis indikasi awal, dan menyimpan linimasa foto komparatif untuk memantau perubahan kondisi kulit dari waktu ke waktu.
5. **Tanpa Hambatan Autentikasi (Zero-Friction / No Auth)**: Pengguna langsung dapat menggunakan seluruh fitur aplikasi tanpa registrasi atau login, dengan data tersimpan aman di basis data lokal Room.

---

## 2. Target Pengguna & Persona

| Tipe Pengguna | Karakteristik & Kebutuhan | Skenario Penggunaan Utama |
| :--- | :--- | :--- |
| **Tenaga Medis Daerah Terpencil (Nakes/Bidan/Dokter Umum)** | Bekerja di puskesmas/klinik dengan sinyal terbatas, butuh referensi dosis obat, panduan praktik klinis (PPK), dan *second opinion* triase. | Memuat folder PDF Pedoman Kemenkes/WHO ke RAG, mencari dosis pediatri, triase kegawatdaruratan. |
| **Masyarakat Umum / Pasien Mandiri** | Butuh informasi kesehatan awal, edukasi penyakit, interpretasi hasil lab, dan pemeriksaan ruam/kulit sebelum pergi ke RS. | Chat gejala demam, foto ruam kulit untuk melihat tingkat urgensi, memantau riwayat tahi lalat/luka. |
| **Pengguna Peduli Privasi** | Mengutamakan keamanan data rekam medis pribadi dan tidak ingin riwayat penyakit diunggah ke cloud. | Pengelolaan rekam medis keluarga lokal, pengingat obat harian, konsultasi kesehatan tertutup. |

---

## 3. Fitur Utama & Kebutuhan Fungsional

```
┌────────────────────────────────────────────────────────────────────────┐
│                        MedBot Core Features                           │
├───────────────────┬───────────────────┬───────────────────┬────────────┤
│ 1. Local Chatbot  │ 2. Knowledge Base │ 3. AI Persona &   │ 4. Skin    │
│    (Gemma + SAF / │    RAG (SAF Dir)  │    Multi-Agent    │    Lineage │
│     Downloader)   │                   │                   │            │
└───────────────────┴───────────────────┴───────────────────┴────────────┘
```

### 3.1 Fitur 1: Chatbot Medis Lokal (Gemma LLM Dual-Mode: SAF & In-App Downloader)
* **Pemuatan Berkas Lokal via SAF**:
  - Pengguna dapat memilih folder atau berkas model lokal menggunakan Android Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE` / `ACTION_OPEN_DOCUMENT`).
  - Aplikasi menyimpan izin akses persisten (`takePersistableUriPermission`).
  - Mendukung model lokal berbasis Gemma (contoh: `Gemma 4 E2B/E4B`, `Gemma 2 2B`, `MedGemma`) dalam format `.litertlm` (LiteRT-LM) atau `.gguf` (llama.cpp).
* **In-App Resumable Model Downloader**:
  - Tombol unduh model langsung di aplikasi (layar *Model Manager*) untuk mengunduh bundle model AI resmi (HuggingFace / direct link CDN).
  - Menggunakan `WorkManager` dan `OkHttp` dengan header HTTP Range (`Range: bytes=X-` / `If-Range`) sehingga proses unduhan dapat dijeda dan dilanjutkan secara otomatis saat jaringan Wi-Fi terhubung kembali.
  - Verifikasi integritas otomatis dengan *checksum* SHA-256 dan *atomic file rename* dari `.part` ke berkas model final.
  - Opsi penyimpanan model yang diunduh ke direktori privat aplikasi atau folder SAF eksternal.
* **Streaming Token Real-Time**:
  - Inferensi menghasilkan respons kata demi kata (token streaming) dengan animasi *typewriter* yang mulus.
  - UI StateFlow dengan *throttling* (sampling 50ms) untuk mencegah *recomposition lag* pada perangkat berspesifikasi menengah.
* **Manajemen Percakapan Lokal**:
  - Riwayat percakapan disimpan secara terstruktur di Room Database.
  - Pengguna dapat membuat sesi percakapan baru, mengganti judul chat, menghapus riwayat, atau mencari isi pesan lama.
  - Kontekstualisasi riwayat percakapan otomatis (*Sliding Window* 6-10 pesan terakhir) untuk menjaga batasan *context window* model.

### 3.2 Fitur 2: Basis Pengetahuan Dokumen RAG (Local RAG via SAF)
* **Integrasi Folder Knowledge Base**:
  - Pengguna dapat memilih folder dokumen medis (misalnya folder berisi PDF Panduan Praktik Klinis, Farmakope Indonesia, Buku Saku Diagnosis) via SAF.
  - Sistem mendeteksi berkas berekstensi `.pdf`, `.txt`, `.md`, dan `.docx`.
* **Pipeline Pemrosesan Dokumen On-Device**:
  - **Text Extraction**: Menggunakan `pdfbox-android` untuk PDF dan native parser untuk plain text/markdown.
  - **Semantic Chunking**: Pemotongan teks adaptif (~512 token per *chunk* dengan *overlap* 50 token) dilengkapi metadata (nama berkas, nomor halaman, bab/seksi).
  - **On-Device Embedding**: Mengubah *chunks* menjadi representasi vektor numerik menggunakan model embedding lokal berukuran ringkas (seperti `Gecko 110M` atau `all-MiniLM-L6-v2` via ONNX/LiteRT).
  - **Vector Storage**: Penyimpanan vektor dalam Room SQLite Database menggunakan representasi BLOB atau ekstensi vektor lokal.
* **Retrieval & Grounded Generation**:
  - Saat pengguna bertanya, kueri diubah menjadi embedding dan dilakukan pencarian *Cosine Similarity* untuk mengambil *Top-K* (3-5) segmen dokumen paling relevan.
  - Menggabungkan konteks dokumen ke dalam *system prompt* LLM.
  - Menampilkan **kartu referensi/sitasi** interaktif di bawah respons AI (menampilkan judul dokumen, halaman, dan cuplikan teks asli yang dapat diklik).

### 3.3 Fitur 3: Persona AI & Sistem Multi-Agent
* **Orkestrator Triase Cerdas**:
  - Menganalisis pesan pengguna dan mengklasifikasikan ke 1-3 spesialis medis yang relevan dari total 46 spesialis medis yang tersedia.
  - Menampilkan lencana agen aktif di atas setiap respons chat (contoh: `🩺 Dokter Umum`, `🧴 Spesialis Kulit`, `👶 Spesialis Anak`).
* **Pengaturan Persona Pengguna**:
  - **Gaya Bahasa / Tone**:
    - *Empatis & Hangat*: Bahasa ramah, menenangkan, mudah dipahami masyarakat awam.
    - *Klinis & Presisi*: Bahasa formal medis, ringkas, menyertakan terminologi klinis dan pertimbangan diagnosis banding.
    - *Ringkas & To-The-Point*: Jawaban langsung pada poin penting dan langkah aksi.
  - **Tingkat Kedalaman Penjelasan**: *Sederhana (Awam)*, *Menengah*, atau *Mendalam (Tenaga Medis)*.
  - **Kustom Prompt Tambahan**: Pengguna dapat menambahkan instruksi sistem tambahan (contoh: "Saya memiliki alergi penisilin dan riwayat hipertensi").
  - **Bahasa**: Pengalihan cepat antara Bahasa Indonesia 🇮🇩 dan English 🇬🇧.

### 3.4 Fitur 4: Diagnosis Lesi Kulit & "Skin Lineage" (Linimasa Visual)
* **Pengambilan Citra Medis**:
  - Integrasi **CameraX** dengan panduan *viewfinder* khusus dermatologi (pengatur fokus, *grid* pembesar, peringatan pencahayaan).
  - Pilihan unggah foto dari galeri lokal (`ActivityResultContracts.PickVisualMedia`).
* **Analisis Visual On-Device**:
  - Menggunakan model multimodal lokal (Vision LLM / LiteRT Vision Model).
  - Evaluasi kaidah klinis **ABCD** untuk tahi lalat/lesi:
    - **A (Asymmetry)**: Kesimetrisan bentuk lesi.
    - **B (Border)**: Keteraturan tepi/batas lesi.
    - **C (Color)**: Variasi warna (merah, cokelat, hitam, pucat).
    - **D (Diameter)**: Estimasi ukuran lesi.
  - Diagnosis Banding Awal: Mengidentifikasi probabilitas kondisi (eksim, dermatitis, infeksi jamur/tinea, jerawat, psoriasis, dll.).
  - Indikator Tingkat Urgensi: Hijau (*Rendah / Rawat Mandiri*), Kuning (*Sedang / Perlu Kontrol Puskesmas*), Merah (*Tinggi / Rujukan Segera*).
* **Skin Lineage (Pelacakan Riwayat & Evolusi)**:
  - Menyimpan setiap rekaman foto kondisi kulit ke dalam entitas `SkinRecord` di Room.
  - Pengelompokan berdasarkan "Area Tubuh" (contoh: Lengan Kiri, Wajah, Punggung).
  - **Tampilan Linimasa & Komparasi**: Tampilan linimasa kronologis yang memungkinkan komparasi *before-after* (slider perbandingan foto) untuk memantau apakah lesi membaik, membesar, atau berubah warna.

### 3.5 Fitur Pendukung & Utilitas Medis Lokal
* **Database Obat & Pemeriksa Interaksi**:
  - Direktori obat esensial lokal (indikasi, dosis dewasa/anak, kontraindikasi, efek samping).
  - Pemeriksa interaksi obat (*drug-to-drug interaction checker*) berbasis aturan lokal.
* **Dasbor Metrik Kesehatan Lokal**:
  - Pencatatan tanda vital mandiri (Tekanan Darah, Gula Darah, Suhu, Berat Badan/BMI).
* **Pengingat Obat Mandiri**:
  - Penjadwalan alarm/notifikasi lokal menggunakan Android `AlarmManager` / `WorkManager`.
* **Ekspor Laporan PDF**:
  - Pembuatan ringkasan konsultasi medis dan riwayat foto lesi ke berkas PDF lokal untuk dibawa saat berkonsultasi ke dokter resmi.

---

## 4. Kebutuhan Non-Fungsional (NFR)

| Aspek | Spesifikasi & Batasan |
| :--- | :--- |
| **Kinerja & Latensi** | - Respon awal (*Time to First Token*): < 800ms pada model yang sudah di-*load* ke memori.<br>- Kecepatan inferensi: > 8-15 token/detik pada chipset modern (MediaTek Dimensity / Snapdragon 8/7 series). |
| **Manajemen Memori (RAM)** | - Alokasi RAM aplikasi terkendali (< 3.5 GB untuk model 2B, < 5.5 GB untuk model 4B).<br>- Deteksi OOM (*Out of Memory*) otomatis dengan mekanisme *fallback* atau peringatan ramah pengguna. |
| **Penyimpanan & Baterai** | - Tidak ada proses *polling* jaringan yang tidak perlu.<br>- Pengunduhan model di latar belakang menggunakan batasan jaringan tidak bermeteran (*unmetered network constraint*).<br>- Batasan *thread execution* inferensi (maksimal 2-4 thread) untuk mencegah *thermal throttling* dan hemat baterai. |
| **Keamanan & Privasi** | - Zero Telemetry: 100% tanpa analitik eksternal.<br>- Penyimpanan lokal aman di *app-private sandbox* dan direktori aman yang dipilih pengguna melalui SAF.<br>- Verifikasi *checksum* SHA-256 untuk seluruh berkas model yang diunduh. |
| **Kompatibilitas** | - Min SDK: 26 (Android 8.0 Oreo).<br>- Target SDK: 35 (Android 15).<br>- Arsitektur CPU: `arm64-v8a`. |

---

## 5. Matriks Perbandingan: MedBot vs Reference Projects

| Fitur / Parameter | Reference Asli (GoldReference) | MedBot (Proyek Baru Ini) |
| :--- | :--- | :--- |
| **Platform / UI** | React Native (0.76) | **Kotlin Native + Jetpack Compose (Material 3)** |
| **Autentikasi** | Firebase Auth (Login / Signup) | **DITIADAKAN (100% Bebas Login / Local Guest Profile)** |
| **Sumber Model AI** | Download HuggingFace / Hardcoded | **Dual-Mode: Pemuatan Folder SAF Lokal + Tombol Unduh Model/Bundle Resumable (WorkManager + HTTP Range dari HuggingFace/CDN)** |
| **RAG Knowledge Base** | Seed Data Hardcoded | **Dinamis via Folder SAF (PDF, TXT, MD Parser + Local Embedder)** |
| **Cloud AI Fallback** | Gemini Cloud API | **DITIADAKAN (Hanya Local On-Device AI)** |
| **Bahasa** | Multi (Termasuk Hindi, Swahili, dll.) | **Bahasa Indonesia 🇮🇩 & English 🇬🇧 Saja** |
| **Pelacakan Kulit** | Analisis Foto Tunggal | **Skin Lineage (Linimasa Kronologis & Komparasi Lesi)** |

---

## 6. Acceptance Criteria (Kriteria Keberhasilan)

1. **Uji Dual-Mode Model**:
   - Pengguna dapat memilih folder lokal yang berisi model `.litertlm` atau `.gguf` via SAF, dan model berhasil dimuat ke RAM.
   - Pengguna dapat menekan tombol **Unduh Model** di aplikasi, proses unduhan berjalan di latar belakang via `WorkManager`, dapat dijeda/dilanjutkan, lolos verifikasi SHA-256, dan siap digunakan untuk inferensi.
2. **Uji Percakapan Medis**: Pengguna dapat mengirim pesan medis, AI merespons dengan *streaming token*, dan jawaban mencerminkan gaya persona yang dipilih.
3. **Uji SAF RAG Dokumen**: Pengguna dapat memilih folder dokumen PDF, aplikasi berhasil mengekstrak teks, membuat *chunks* dan embedding ke Room DB, serta menyertakan kutipan dokumen saat ditanya mengenai materi dokumen tersebut.
4. **Uji Skin Lineage**: Pengguna dapat mengambil foto kulit via kamera/galeri, menerima hasil evaluasi ABCD, dan melihat entitas tersebut tercatat dalam linimasa riwayat kulit.
5. **Uji Mode Pesawat**: Seluruh fungsionalitas inferensi AI, RAG, dan diagnosis kulit berjalan sempurna saat perangkat berada dalam *Airplane Mode* (tanpa internet sama sekali).

