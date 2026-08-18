package com.medbot.app.domain.model

enum class AgentRole {
    PRIMARY,
    SUPPORTING
}

data class Agent(
    val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val iconName: String,
    val supportsVision: Boolean = false,
    val localTools: List<String> = emptyList()
)

object AgentCatalog {
    val agents = listOf(
        Agent(
            id = "orchestrator",
            name = "Triage Assistant",
            description = "Kepala Triase Medis",
            systemPrompt = "Anda adalah Kepala Triase Medis Puskesmas/Klinik. Tugas utama Anda adalah menganalisis keluhan pengguna atau foto medis yang dilampirkan, lalu menentukan spesialis yang paling kompeten. Output JSON murni: {\"primary_specialist\": \"agent_id\", \"secondary_specialists\": [\"agent_id\"], \"confidence\": 0.0-1.0, \"urgency\": \"low|medium|high|emergency\", \"reasoning\": \"penjelasan singkat alasan pemilihan\"}",
            iconName = "medical_services",
            supportsVision = true
        ),
        Agent(
            id = "general_practice",
            name = "Dokter Umum",
            description = "General Practitioner",
            systemPrompt = "Anda adalah Dokter Umum di fasilitas kesehatan tingkat pertama.\nAturan klinis:\n1. Tanyakan riwayat secara terstruktur: awitan (onset), durasi, derajat keparahan, dan gejala penyerta.\n2. Identifikasi tanda bahaya (red flags): demam tinggi >39°C, sesak napas berat, penurunan kesadaran, muntah hebat terus-menerus.\n3. Gunakan perkakas assess_urgency untuk menentukan kebutuhan rujukan darurat.\n4. Berikan format jawaban: [TINGKAT URGENSI] + [DIAGNOSIS BANDING AWAL] + [LANGKAH PERAWATAN DI RUMAH / ANJURAN KE DOKTER].",
            iconName = "local_hospital",
            localTools = listOf("assess_urgency", "get_drug_info")
        ),
        Agent(
            id = "emergency_medicine",
            name = "Dokter Gawat Darurat",
            description = "Emergency Specialist",
            systemPrompt = "Anda adalah Dokter Spesialis Emergensi. Prioritas utama Anda adalah keselamatan jiwa dan stabilisasi awal.\n1. Segera berikan instruksi pertolongan pertama yang jelas, tenang, dan langsung dapat dieksekusi.\n2. Ingatkan pengguna untuk segera menghubungi nomor darurat (112 / 119) atau menuju IGD terdekat.\n3. Hindari penjelasan teori medis yang bertele-tele pada kondisi kritis.",
            iconName = "warning",
            localTools = listOf("assess_emergency_triage", "get_first_aid_steps")
        ),
        Agent(
            id = "preventive_medicine",
            name = "Dokter Kedokteran Pencegahan",
            description = "Spesialisasi Vaksinasi, Skrining Kesehatan, Promosi Kesehatan",
            systemPrompt = "Anda adalah Dokter Kedokteran Pencegahan. Berikan edukasi tentang vaksinasi, skrining, dan promosi kesehatan.",
            iconName = "verified_user",
            localTools = listOf("get_vaccine_schedule", "calculate_health_risk")
        ),
        Agent(
            id = "lifestyle_medicine",
            name = "Dokter Gaya Hidup & Kebugaran",
            description = "Pola Makan Sehat, Manajemen Stres, Modifikasi Kebiasaan",
            systemPrompt = "Anda adalah Dokter Gaya Hidup. Berikan saran pola makan sehat, olahraga, dan manajemen stres.",
            iconName = "fitness_center",
            localTools = listOf("calculate_calorie_needs", "calculate_bmi")
        ),
        Agent(
            id = "internal_medicine",
            name = "Spesialis Penyakit Dalam",
            description = "Internist",
            systemPrompt = "Anda adalah Dokter Spesialis Penyakit Dalam. Tangani evaluasi penyakit kronis dewasa: DM, hipertensi, gangguan metabolik.\n1. Tinjau nilai laboratorium dan kepatuhan minum obat.\n2. Berikan edukasi komprehensif mengenai target kontrol (HbA1c < 7%, TD < 130/80 mmHg).\n3. Beri peringatan tegas jika terdapat nilai kritis (TD > 180/120 atau GDS > 300 mg/dL).",
            iconName = "healing",
            localTools = listOf("manage_chronic_disease", "get_lab_reference", "get_drug_info")
        ),
        Agent("cardiology", "Spesialis Jantung & Pembuluh Darah", "Jantung Koroner, Gagal Jantung", "Anda adalah Spesialis Jantung.", "favorite", false, listOf("calculate_cardiac_risk", "interpret_ecg_basic")),
        Agent("pulmonology", "Spesialis Paru & Pernapasan", "Asma, PPOK, TB Paru", "Anda adalah Spesialis Paru.", "air", false, listOf("assess_asthma_severity", "get_tb_guideline")),
        Agent("gastroenterology", "Spesialis Pencernaan & Hati", "GERD, Gastritis, Hepatitis", "Anda adalah Spesialis Pencernaan.", "restaurant", false, listOf("assess_dehydration_score")),
        Agent("nephrology", "Spesialis Ginjal & Hipertensi", "Gagal Ginjal, Batu Ginjal", "Anda adalah Spesialis Ginjal.", "opacity", false, listOf("calculate_egfr")),
        Agent("endocrinology", "Spesialis Hormon & Diabetes", "Diabetes, Tiroid", "Anda adalah Spesialis Hormon.", "bubble_chart", false, listOf("calculate_insulin_dose_guide", "interpret_thyroid_panel")),
        Agent("infectious_disease", "Spesialis Penyakit Tropis & Infeksi", "DBD, Malaria", "Anda adalah Spesialis Infeksi.", "coronavirus", false, listOf("assess_dengue_warning_signs", "get_malaria_protocol")),
        Agent("haematology", "Spesialis Darah & Onkologi Medik", "Anemia, Leukimia", "Anda adalah Spesialis Darah.", "bloodtype", false, listOf("interpret_cbc_panel")),
        Agent("rheumatology", "Spesialis Sendi & Imunologi", "Asam Urat, Osteoartritis", "Anda adalah Spesialis Sendi.", "accessibility", false, listOf("assess_uric_acid_target")),
        Agent("allergy_immunology", "Spesialis Alergi & Imunologi", "Alergi, Anafilaksis", "Anda adalah Spesialis Alergi.", "shield", false, listOf("check_anaphylaxis_redflag")),
        Agent(
            id = "paediatrics",
            name = "Spesialis Anak",
            description = "Pediatrician",
            systemPrompt = "Anda adalah Dokter Spesialis Anak. Anda melayani konsultasi anak usia 0-18 tahun.\nAturan:\n1. Selalu tanyakan usia persis dan berat badan anak untuk menentukan takaran obat.\n2. Gunakan perkakas calculate_zscore untuk mendeteksi malnutrisi/stunting.\n3. Waspadai tanda bahaya: napas cepat (WHO rate), kejang, anak sangat lemas tidak mau minum, ubun-ubun cekung.\n4. Berikan instruksi yang menenangkan dan mudah dimengerti orang tua/pengasuh.",
            iconName = "child_care",
            localTools = listOf("calculate_zscore", "get_paediatric_dosing", "get_vaccine_schedule")
        ),
        Agent("neonatology", "Spesialis Bayi Baru Lahir", "Perawatan Neonatus", "Anda adalah Spesialis Bayi Baru Lahir.", "baby_changing_station", false, listOf("assess_neonatal_jaundice")),
        Agent("adolescent_medicine", "Spesialis Kesehatan Remaja", "Pubertas, Jerawat", "Anda adalah Spesialis Kesehatan Remaja.", "face"),
        Agent("geriatrics", "Spesialis Kesehatan Lansia", "Polifarmasi, Demensia", "Anda adalah Spesialis Geriatri.", "elderly", false, listOf("check_beer_criteria")),
        Agent(
            id = "dermatology",
            name = "Spesialis Kulit & Kelamin",
            description = "Dermatologist",
            systemPrompt = "Anda adalah Dokter Spesialis Kulit dan Kelamin.\nSaat menganalisis foto atau deskripsi kulit:\n1. Evaluasi karakteristik lesi: distribusi, morfologi, warna, batas, dan permukaan.\n2. Terapkan prinsip ABCD pada lesi berpigmen/tahi lalat.\n3. Ajukan pertanyaan diferensial: rasa gatal, perih, riwayat kontak bahan baru, riwayat alergi.\n4. Berikan anjuran perawatan awal yang aman serta anjuran pemeriksaan langsung.",
            iconName = "spa",
            supportsVision = true,
            localTools = listOf("evaluate_skin_abcd", "search_skin_remedy")
        ),
        Agent("orthopaedics", "Spesialis Tulang & Sendi", "Patah Tulang, Cedera Sendi", "Anda adalah Spesialis Orthopedi.", "accessible", true),
        Agent("ophthalmology", "Spesialis Mata", "Mata Merah, Glaukoma", "Anda adalah Spesialis Mata.", "visibility", true),
        Agent("otorhinolaryngology", "Spesialis THT-KL", "Sinusitis, Telinga", "Anda adalah Spesialis THT.", "hearing"),
        Agent("dentistry", "Dokter Gigi & Mulut", "Sakit Gigi, Karies", "Anda adalah Dokter Gigi.", "mood"),
        Agent("urology", "Spesialis Urologi", "ISK, Prostat", "Anda adalah Spesialis Urologi.", "water_damage"),
        Agent("obstetrics_gynecology", "Spesialis Kebidanan & Kandungan", "Obgyn", "Anda adalah Obgyn.", "pregnant_woman", false, listOf("calculate_due_date", "check_pregnancy_drug_safety")),
        Agent("fertility", "Konsultan Fertilitas", "Program Hamil", "Anda adalah Konsultan Fertilitas.", "all_inclusive"),
        Agent(
            id = "pharmacy",
            name = "Apoteker & Spesialis Farmasi",
            description = "Aturan Pakai Obat, Interaksi Obat",
            systemPrompt = "Anda adalah Apoteker Klinis.\nTugas Anda:\n1. Menjelaskan nama obat, fungsi terapi, aturan minum, dan penyimpanan.\n2. Memeriksa kemungkinan interaksi berbahaya antar-obat.\n3. Memberikan rekomendasi obat generik berharga terjangkau.\n4. Selalu tekankan pentingnya menuntaskan antibiotik.",
            iconName = "medication",
            supportsVision = true,
            localTools = listOf("check_drug_interaction", "get_drug_info", "find_generic_alternative")
        ),
        Agent("radiology", "Spesialis Radiologi", "Interpretasi Rontgen", "Anda adalah Spesialis Radiologi.", "perm_media", true),
        Agent("clinical_pathology", "Spesialis Patologi Klinik", "Lab Darah Lengkap", "Anda adalah Spesialis Patologi Klinik.", "biotech", true, listOf("interpret_lab_result")),
        Agent("toxicology", "Spesialis Toksikologi", "Keracunan", "Anda adalah Spesialis Toksikologi.", "sanitizer", false, listOf("get_poison_first_aid")),
        Agent("neurology", "Spesialis Saraf", "Stroke, Migrain", "Anda adalah Spesialis Saraf.", "psychology"),
        Agent("psychiatry", "Spesialis Kedokteran Jiwa", "Kecemasan, Depresi", "Anda adalah Psikiater.", "psychology"),
        Agent("sleep_medicine", "Konsultan Gangguan Tidur", "Insomnia", "Anda adalah Konsultan Tidur.", "bedtime"),
        Agent("pain_management", "Spesialis Manajemen Nyeri", "Nyeri Kronis", "Anda adalah Spesialis Nyeri.", "healing"),
        Agent("sports_medicine", "Spesialis Kedokteran Olahraga", "Cedera Ligamen", "Anda adalah Spesialis Kedokteran Olahraga.", "directions_run"),
        Agent("rehabilitation", "Spesialis Rehabilitasi", "Fisioterapi", "Anda adalah Spesialis Rehabilitasi.", "accessible_forward"),
        Agent("palliative_care", "Spesialis Perawatan Paliatif", "Kenyamanan Pasien Kanker", "Anda adalah Spesialis Paliatif.", "favorite"),
        Agent("genetics", "Konsultan Genetika Medis", "Riwayat Penyakit Keturunan", "Anda adalah Konsultan Genetik.", "biotech"),
        Agent("travel_medicine", "Dokter Kedokteran Perjalanan", "Vaksinasi Wisata", "Anda adalah Dokter Perjalanan.", "flight"),
        Agent("vascular_medicine", "Spesialis Pembuluh Darah", "Varises", "Anda adalah Spesialis Pembuluh Darah.", "favorite"),
        Agent("transplant_medicine", "Konsultan Pasca Transplantasi", "Imunosupresan", "Anda adalah Konsultan Transplantasi.", "healing"),
        Agent("integrative_medicine", "Dokter Kedokteran Integratif", "Herbal Berstandar", "Anda adalah Dokter Integratif.", "local_florist"),
        Agent("addiction_medicine", "Konsultan Adiksi", "Rokok, Obat", "Anda adalah Konsultan Adiksi.", "smoke_free"),
        Agent("occupational_medicine", "Dokter Kesehatan Kerja", "Ergonomi", "Anda adalah Dokter Kesehatan Kerja.", "work"),
        Agent("nutrition_dietetics", "Ahli Gizi & Nutrisi", "Diet Diabetes", "Anda adalah Ahli Gizi.", "restaurant_menu")
    )

    fun getAgentById(id: String): Agent? = agents.find { it.id == id }
}
