# Product Requirements Document (PRD)
## MedBot — On-Device Medical AI Assistant & Health Companion

| Dokumen | Product Requirements Document (PRD) |
| :--- | :--- |
| **Proyek** | MedBot (Android Jetpack Compose) |
| **Versi** | 1.0.0 |
| **Status** | Approved / Ready for Implementation |
| **Target OS** | Android 12+ (API 31) s.d. Android 15 (API 35) |
| **Bahasa Utama** | Bahasa Indonesia (Default) & English |
| **Konektivitas** | Offline-First (Inferensi AI 100% Lokal/On-Device; Internet hanya digunakan opsional untuk unduh bundle model AI) |

---

## 1. Executive Summary & Visi Produk

### 1.1 Latar Belakang
Akses terhadap layanan kesehatan berkualitas dan konsultasi medis cepat sering kali terbatas di wilayah terpencil, kepulauan, dan pedesaan (wilayah 3T). Di sisi lain, privasi data kesehatan merupakan isu krusial di mana pengguna enggan membagikan riwayat penyakit pribadi dan foto kondisi medis ke server cloud pihak ketiga.

**MedBot** adalah aplikasi Android berbasis **Jetpack Compose** yang berfungsi sebagai asisten informasi kesehatan privat dan local-first. Triase aturan keselamatan, persona, penyimpanan, dan UI berjalan di perangkat. Inferensi LLM, RAG, dan vision hanya aktif setelah runtime, model, dokumen, embedder, atau foto nyata tervalidasi; aplikasi tidak mengklaim diagnosis atau hasil klinis tanpa evidence gate.

### 1.2 Pilar Utama Produk
1. **Privasi Mutlak & 100% Offline Inference**: Tidak ada data medis, teks chat, atau citra foto yang keluar dari perangkat. Seluruh inferensi teks dan visi berjalan di perangkat.
2. **Pemuatan Model AI yang Terverifikasi**:
   - **Pemuatan Berkas Berbasis SAF**: Memuat berkas model lokal `.litertlm` dan dokumen RAG yang dipilih pengguna melalui SAF.
   - **In-App Resumable Model Downloader**: Tombol unduh hanya boleh aktif untuk manifest resmi yang memiliki URL HTTPS, ukuran, SHA-256, dan provenance terverifikasi. Tanpa manifest tersebut, UI tetap `MODEL_UNAVAILABLE`.
3. **Multi-Agent & Persona Adaptif**: Registry 46 agen spesialis menyediakan routing dan konteks persona. Output AI tetap memerlukan runtime model lokal yang benar-benar loaded.
4. **Dermatologi Visual & Skin Lineage**: Kemampuan menangkap/mengimpor foto nyata dan menyimpan linimasa lokal. Analisis visual tetap `UNAVAILABLE` sampai model vision lokal tervalidasi tersedia; tidak ada baseline benign atau diagnosis sintetis.
5. **Tanpa Hambatan Autentikasi (Zero-Friction / No Auth)**: Pengguna langsung dapat menggunakan seluruh fitur aplikasi tanpa registrasi atau login, dengan data tersimpan aman di basis data lokal Room.

---

## 2. Target Pengguna & Persona

| Tipe Pengguna | Karakteristik & Kebutuhan | Skenario Penggunaan Utama |
| :--- | :--- | :--- |
 | **Tenaga Medis Daerah Terpencil (Nakes/Bidan/Dokter Umum)** | Bekerja di puskesmas/klinik dengan sinyal terbatas, butuh sumber yang disediakan organisasi dan triase keselamatan. | Memuat dokumen milik pengguna melalui SAF, mencari informasi yang memiliki provenance, dan menjalankan triase kegawatdaruratan. |
| **Masyarakat Umum / Pasien Mandiri** | Butuh informasi kesehatan awal, edukasi penyakit, interpretasi hasil lab, dan pemeriksaan ruam/kulit sebelum pergi ke RS. | Chat gejala demam, foto ruam kulit untuk melihat tingkat urgensi, memantau riwayat tahi lalat/luka. |
| **Pengguna Peduli Privasi** | Mengutamakan keamanan data rekam medis pribadi dan tidak ingin riwayat penyakit diunggah ke cloud. | Pengelolaan rekam medis keluarga lokal, pengingat obat harian, konsultasi kesehatan tertutup. |

---

## 3. Fitur Utama & Kebutuhan Fungsional

```
┌────────────────────────────────────────────────────────────────────────┐
│                        MedBot Core Features                           │
├───────────────────┬───────────────────┬───────────────────┬────────────┤
│ 1. Local Chatbot  │ 2. Knowledge Base │ 3. AI Persona &   │ 4. Skin    │
│    (LiteRT-LM +   │    RAG (SAF)      │    Multi-Agent    │    Lineage │
│     SAF/manifest) │                   │                   │            │
└───────────────────┴───────────────────┴───────────────────┴────────────┘
```

### 3.1 Fitur 1: Chatbot Medis Lokal (LiteRT-LM dan evidence gate)
* **Pemuatan Berkas Lokal via SAF**:
  - Pengguna dapat memilih berkas model lokal menggunakan Android Storage Access Framework (`ACTION_OPEN_DOCUMENT`).
  - Aplikasi menyimpan izin akses persisten (`takePersistableUriPermission`).
   - Menerima model LiteRT-LM `.litertlm`; format lain ditolak oleh runtime ini.
* **In-App Resumable Model Downloader**:
   - Tombol unduh model langsung di aplikasi (layar *Model Manager*) hanya tersedia setelah manifest resmi dengan metadata integritas lengkap diverifikasi.
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
   - Pengguna dapat memilih dokumen medis melalui SAF. Aplikasi tidak menyertakan corpus klinis bawaan atau dokumen sintetis.
  - Sistem mendeteksi berkas berekstensi `.pdf`, `.txt`, `.md`, dan `.docx`.
* **Pipeline Pemrosesan Dokumen On-Device**:
  - **Text Extraction**: Menggunakan `pdfbox-android` untuk PDF dan native parser untuk plain text/markdown.
  - **Semantic Chunking**: Pemotongan teks adaptif (~512 token per *chunk* dengan *overlap* 50 token) dilengkapi metadata (nama berkas, nomor halaman, bab/seksi).
   - **On-Device Embedding**: Mengubah *chunks* menjadi representasi vektor numerik menggunakan model embedding lokal yang benar-benar tersedia dan telah diverifikasi. Tanpa model tersebut, statusnya `EMBEDDER_UNAVAILABLE`.
  - **Vector Storage**: Penyimpanan vektor dalam Room SQLite Database menggunakan representasi BLOB atau ekstensi vektor lokal.
* **Retrieval & Grounded Generation**:
  - Saat pengguna bertanya, kueri diubah menjadi embedding dan dilakukan pencarian *Cosine Similarity* untuk mengambil *Top-K* (3-5) segmen dokumen paling relevan.
  - Menggabungkan konteks dokumen ke dalam *system prompt* LLM.
   - Menampilkan referensi/sitasi interaktif hanya dari metadata dokumen asli yang dipilih pengguna (judul, halaman bila authoritative, dan cuplikan teks asli).

### 3.3 Fitur 3: Persona AI & Sistem Multi-Agent
* **Orkestrator Triase Cerdas**:
  - Menganalisis pesan pengguna dan mengklasifikasikan ke 1-3 spesialis medis yang relevan dari total 46 spesialis medis yang tersedia.
   - Menampilkan nama agen aktif dengan teks terlokalisasi; emoji bukan bagian dari UI.
* **Pengaturan Persona Pengguna**:
  - **Gaya Bahasa / Tone**:
    - *Empatis & Hangat*: Bahasa ramah, menenangkan, mudah dipahami masyarakat awam.
    - *Klinis & Presisi*: Bahasa formal medis, ringkas, menyertakan terminologi klinis dan pertimbangan diagnosis banding.
    - *Ringkas & To-The-Point*: Jawaban langsung pada poin penting dan langkah aksi.
  - **Tingkat Kedalaman Penjelasan**: *Sederhana (Awam)*, *Menengah*, atau *Mendalam (Tenaga Medis)*.
  - **Kustom Prompt Tambahan**: Pengguna dapat menambahkan instruksi sistem tambahan (contoh: "Saya memiliki alergi penisilin dan riwayat hipertensi").
  - **Bahasa**: Pengalihan cepat antara Bahasa Indonesia dan English.

### 3.4 Fitur 4: Evidence-Gated Skin Vision & "Skin Lineage" (Linimasa Visual)
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
   - Diagnosis banding dan probabilitas tidak ditampilkan tanpa model vision lokal tervalidasi dan kontrak output klinis yang disetujui.
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
 | **Kompatibilitas** | - Min SDK: 31 (Android 12).<br>- Target SDK: 35 (Android 15).<br>- Arsitektur CPU: `arm64-v8a`. |

---

## 5. Matriks Perbandingan: MedBot vs Reference Projects

| Fitur / Parameter | Reference Asli (GoldReference) | MedBot (Proyek Baru Ini) |
| :--- | :--- | :--- |
| **Platform / UI** | React Native (0.76) | **Kotlin Native + Jetpack Compose (Material 3)** |
| **Autentikasi** | Firebase Auth (Login / Signup) | **DITIADAKAN (100% Bebas Login / Local Guest Profile)** |
 | **Sumber Model AI** | Download / hardcoded | **Berkas `.litertlm` dari SAF atau manifest resmi terverifikasi; tanpa cloud fallback** |
| **RAG Knowledge Base** | Seed Data Hardcoded | **Dinamis via file SAF yang dipilih pengguna (parser nyata + embedder lokal; unavailable tanpa embedder)** |
| **Cloud AI Fallback** | Gemini Cloud API | **DITIADAKAN (Hanya Local On-Device AI)** |
| **Bahasa** | Multi (Termasuk Hindi, Swahili, dll.) | **Bahasa Indonesia & English saja** |
| **Pelacakan Kulit** | Analisis Foto Tunggal | **Skin Lineage (Linimasa Kronologis & Komparasi Lesi)** |

---

## 6. Acceptance Criteria (Kriteria Keberhasilan)

1. **Uji Dual-Mode Model**:
   - Pengguna dapat memilih berkas `.litertlm` via SAF. Model hanya dinyatakan loaded setelah validasi berkas, checksum/ukuran yang tersedia, backend, dan inisialisasi LiteRT-LM berhasil.
    - Pengguna dapat menekan tombol **Unduh Model** hanya ketika manifest resmi tersedia. Proses unduhan berjalan via `WorkManager`, dapat dijeda/dilanjutkan, lolos verifikasi SHA-256, dan siap digunakan untuk inferensi.
2. **Uji Percakapan Medis**: Pengguna dapat mengirim pesan medis, AI merespons dengan *streaming token*, dan jawaban mencerminkan gaya persona yang dipilih.
3. **Uji SAF RAG Dokumen**: Dengan dokumen nyata dan embedder lokal nyata, aplikasi mengekstrak teks, membuat chunks, menyimpan provenance, dan menyertakan kutipan asli. Tanpa embedder/dokumen, status wajib `EMBEDDER_UNAVAILABLE` atau `BLOCKED`.
4. **Uji Skin Lineage**: Pengguna dapat mengambil/mengimpor foto nyata dan menyimpannya sebagai lineage. Hasil ABCD/vision hanya dapat diterima jika model vision lokal nyata dan kontrak tervalidasi tersedia; jika tidak, status `UNAVAILABLE`.
5. **Uji Mode Pesawat**: Jalur yang sudah memiliki asset lokal nyata diuji tanpa internet. Fitur yang kekurangan model, embedder, dokumen, atau foto dilaporkan `BLOCKED`/`UNAVAILABLE`, bukan dinyatakan sempurna.
