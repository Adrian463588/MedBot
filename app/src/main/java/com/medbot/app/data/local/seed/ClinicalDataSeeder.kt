package com.medbot.app.data.local.seed

import com.medbot.app.data.local.dao.DrugDao
import com.medbot.app.data.local.dao.HealthToolsDao
import com.medbot.app.data.local.entities.DrugEntity
import com.medbot.app.data.local.entities.DrugInteractionEntity
import com.medbot.app.data.local.entities.LabTestEntity
import com.medbot.app.data.local.entities.SkinRemedyEntity

object ClinicalDataSeeder {

    suspend fun seedInitialData(drugDao: DrugDao, healthToolsDao: HealthToolsDao) {
        if (drugDao.getDrugCount() == 0) {
            drugDao.insertDrugs(INITIAL_DRUGS)
            drugDao.insertInteractions(INITIAL_INTERACTIONS)
            drugDao.insertSkinRemedies(INITIAL_SKIN_REMEDIES)
        }
        if (healthToolsDao.getLabTestCount() == 0) {
            healthToolsDao.insertLabTests(INITIAL_LAB_TESTS)
        }
    }

    private val INITIAL_DRUGS = listOf(
        DrugEntity(
            name = "Paracetamol",
            genericName = "Acetaminophen",
            category = "Analgesik & Antipiretik",
            indication = "Meredakan demam, sakit kepala, sakit gigi, dan nyeri ringan-sedang.",
            adultDose = "500 mg - 1000 mg tiap 4-6 jam (Maksimal 4000 mg / hari).",
            childDose = "10 - 15 mg/kgBB tiap 4-6 jam (Maksimal 4 kali sehari).",
            contraindications = "Hipersensitivitas, gangguan fungsi hati berat.",
            sideEffects = "Jarang: ruam kulit, hepatotoksisitas pada dosis berlebih.",
            isOtc = true,
            alternativesJson = "[\"Panadol\", \"Sanmol\", \"Pamol\", \"Biogesic\"]"
        ),
        DrugEntity(
            name = "Ibuprofen",
            genericName = "Ibuprofen",
            category = "OAINS (Antiinflamasi Non-Steroid)",
            indication = "Meredakan nyeri radang, dismenore (nyeri haid), nyeri sendi, sakit gigi, dan demam.",
            adultDose = "200 mg - 400 mg tiap 6-8 jam sesudah makan (Maksimal 1200 mg / hari tanpa resep).",
            childDose = "5 - 10 mg/kgBB tiap 6-8 jam sesudah makan.",
            contraindications = "Tukak lambung aktif, riwayat perdarahan saluran cerna, gagal jantung berat, kehamilan trimester 3.",
            sideEffects = "Mual, dispepsia, nyeri ulu hati, pusing.",
            isOtc = true,
            alternativesJson = "[\"Proris\", \"Brufen\", \"Bufect\", \"Farsifen\"]"
        ),
        DrugEntity(
            name = "Amoxicillin",
            genericName = "Amoksisilin Trihidrat",
            category = "Antibiotik Penisilin",
            indication = "Infeksi bakteri saluran pernapasan atas/bawah, infeksi telinga (otitis media), ISK, dan infeksi kulit.",
            adultDose = "500 mg tiap 8 jam atau 875 mg tiap 12 jam selama 5-7 hari.",
            childDose = "25 - 50 mg/kgBB/hari dibagi dalam 3 dosis tiap 8 jam.",
            contraindications = "Riwayat alergi penisilin/beta-laktam.",
            sideEffects = "Diare, mual, ruam kulit, reaksi alergi.",
            isOtc = false,
            alternativesJson = "[\"Amoxil\", \"Amoxsan\", \"Yusimox\", \"Hupamox\"]"
        ),
        DrugEntity(
            name = "Amlodipine",
            genericName = "Amlodipin Besilat",
            category = "Antihipertensi (Calcium Channel Blocker)",
            indication = "Pengobatan hipertensi dan angina pektoris stabil.",
            adultDose = "5 mg sekali sehari, dapat ditingkatkan hingga 10 mg sekali sehari.",
            childDose = "Hanya atas pengawasan dokter spesialis anak.",
            contraindications = "Hipotensi berat, syok kardiogenik, stenosis aorta berat.",
            sideEffects = "Edema perifer (kaki bengkak), sakit kepala, kemerahan (flushing), kelelahan.",
            isOtc = false,
            alternativesJson = "[\"Norvask\", \"Tensivask\", \"Divask\", \"Lodipin\"]"
        ),
        DrugEntity(
            name = "Metformin",
            genericName = "Metformin HCl",
            category = "Antidiabetes Oral (Biguanid)",
            indication = "Lini pertama pengobatan Diabetes Melitus Tipe 2.",
            adultDose = "500 mg 1-2 kali sehari saat makan, dosis maksimal 2000-2500 mg/hari.",
            childDose = "Usia > 10 tahun: 500 mg 1-2 kali sehari.",
            contraindications = "Gagal ginjal (eGFR < 30 mL/min), asidosis metabolik akut/kronis, gagal hati.",
            sideEffects = "Mual, kembung, diare ringan, rasa logam di mulut.",
            isOtc = false,
            alternativesJson = "[\"Glucophage\", \"Gludatic\", \"Forbetes\", \"Diaversa\"]"
        ),
        DrugEntity(
            name = "Omeprazole",
            genericName = "Omeprazol",
            category = "Penekan Asam Lambung (PPI)",
            indication = "Tukak lambung, tukak duodenum, GERD (asam lambung naik), sindrom Zollinger-Ellison.",
            adultDose = "20 mg - 40 mg sekali sehari, diminum 30-60 menit sebelum sarapan.",
            childDose = "0.7 - 1.4 mg/kgBB/hari (hanya dengan resep).",
            contraindications = "Hipersensitivitas terhadap PPI.",
            sideEffects = "Sakit kepala, diare, konstipasi, nyeri perut.",
            isOtc = true,
            alternativesJson = "[\"Prilosec\", \"Losec\", \"Omevell\", \"Inhipump\"]"
        ),
        DrugEntity(
            name = "Cetirizine",
            genericName = "Setirizin Dihidroklorida",
            category = "Antihistamin Generasi 2",
            indication = "Rinitis alergi, urtikaria (biduran/kaligata), gatal kulit karena alergi.",
            adultDose = "10 mg sekali sehari malam hari.",
            childDose = "2-6 tahun: 2.5 mg 1-2x sehari; >6 tahun: 5-10 mg sekali sehari.",
            contraindications = "Gagal ginjal stadium akhir (CrCl < 10 mL/min).",
            sideEffects = "Sedikit mengantuk, mulut kering, lelah.",
            isOtc = true,
            alternativesJson = "[\"Ryvel\", \"Incidal-OD\", \"Ozen\", \"Cerini\"]"
        ),
        DrugEntity(
            name = "Oralit",
            genericName = "Garam Rehidrasi Oral (Oral Rehydration Salts)",
            category = "Cairan Rehidrasi Elektrolit",
            indication = "Mencegah dan mengatasi dehidrasi akibat diare dan muntah pada anak dan dewasa.",
            adultDose = "1-2 sachet dilarutkan dalam 200 ml air matang setiap kali buang air besar cair.",
            childDose = "50-100 ml per BAB cair untuk anak < 2 th; 100-200 ml untuk anak > 2 th.",
            contraindications = "Obstruksi usus, muntah terus menerus yang tidak tertangani.",
            sideEffects = "Sangat aman; rasa haus berkurang secara bertahap.",
            isOtc = true,
            alternativesJson = "[\"Pharolit\", \"Corsalit\", \"Pedialyte\"]"
        ),
        DrugEntity(
            name = "Salbutamol",
            genericName = "Salbutamol Sulfat",
            category = "Bronkodilator (Beta-2 Agonis)",
            indication = "Pereda cepat sesak napas pada serangan asma dan bronkospasme PPOK.",
            adultDose = "Inhaler: 1-2 hisapan (100-200 mcg) saat serangan sesak; Tablet: 2-4 mg 3-4 kali sehari.",
            childDose = "Inhaler: 1 hisapan (100 mcg); Tablet sirup: 0.1 mg/kgBB/kali.",
            contraindications = "Hipersensitivitas, riwayat aritmia berat.",
            sideEffects = "Tremor halus pada jari, palpitasi (jantung berdebar), sakit kepala.",
            isOtc = false,
            alternativesJson = "[\"Ventolin\", \"Astharol\", \"Lasal\", \"Fartolin\"]"
        ),
        DrugEntity(
            name = "Simvastatin",
            genericName = "Simvastatin",
            category = "Penurun Kolesterol (Statin)",
            indication = "Hiperkolesterolemia, pencegahan sekunder penyakit jantung koroner.",
            adultDose = "10 mg - 20 mg sekali sehari diminum malam hari sebelum tidur.",
            childDose = "Hanya pada hiperkolesterolemia familial sesuai anjuran spesialis.",
            contraindications = "Penyakit hati aktif, kehamilan, menyusui.",
            sideEffects = "Mialgia (nyeri otot), peningkatan enzim transaminase hati.",
            isOtc = false,
            alternativesJson = "[\"Zocor\", \"Lipitor (Atorvastatin)\", \"Selvim\", \"Mersikol\"]"
        )
    )

    private val INITIAL_INTERACTIONS = listOf(
        DrugInteractionEntity(
            drugA = "Amlodipine",
            drugB = "Simvastatin",
            severity = "MODERATE",
            description = "Amlodipin meningkatkan kadar Simvastatin dalam darah sehingga meningkatkan risiko miopati dan rhabdomyolysis (kerusakan otot).",
            recommendation = "Batasi dosis Simvastatin maksimal 20 mg per hari jika dikonsumsi bersamaan dengan Amlodipin."
        ),
        DrugInteractionEntity(
            drugA = "Ibuprofen",
            drugB = "Amlodipine",
            severity = "MODERATE",
            description = "OAINS seperti Ibuprofen dapat menurunkan efektivitas antihipertensi dari Amlodipin dan membebani fungsi ginjal.",
            recommendation = "Gunakan Paracetamol sebagai alternatif antinyeri, atau pantau tekanan darah secara berkala."
        ),
        DrugInteractionEntity(
            drugA = "Metformin",
            drugB = "Cimetidine",
            severity = "MODERATE",
            description = "Simetidin dapat menghambat ekskresi ginjal Metformin sehingga meningkatkan risiko asidosis laktat.",
            recommendation = "Gunakan Omeprazole atau Ranitidine sebagai alternatif obat lambung jika diperlukan."
        ),
        DrugInteractionEntity(
            drugA = "Amoxicillin",
            drugB = "Allopurinol",
            severity = "MINOR",
            description = "Penggunaan bersamaan dapat meningkatkan insiden ruam kulit (skin rash).",
            recommendation = "Segera hubungi dokter jika muncul ruam kemerahan yang meluas."
        )
    )

    private val INITIAL_LAB_TESTS = listOf(
        LabTestEntity(
            testName = "Hemoglobin (Hb)",
            category = "Hematologi Darah Lengkap",
            unit = "g/dL",
            normalLow = 13.0,
            normalHigh = 17.5,
            interpretationLow = "Anemia (Kurang Darah). Dapat disebabkan oleh defisiensi zat besi, perdarahan, atau penyakit kronis.",
            interpretationHigh = "Polisitemia / Hemokonsentrasi. Dapat terjadi akibat dehidrasi berat, merokok, atau kelainan sumsum tulang.",
            clinicalSignificance = "Parameter pengangkut oksigen utama dalam darah."
        ),
        LabTestEntity(
            testName = "Leukosit (Sel Darah Putih)",
            category = "Hematologi Darah Lengkap",
            unit = "/µL",
            normalLow = 4000.0,
            normalHigh = 10000.0,
            interpretationLow = "Leukopenia. Risiko infeksi meningkat; dapat disebabkan oleh infeksi virus (seperti DBD) atau supresi imun.",
            interpretationHigh = "Leukositosis. Tanda utama infeksi bakteri, inflamasi akut, stres fisik, atau reaksi obat.",
            clinicalSignificance = "Indikator utama respons kekebalan tubuh terhadap infeksi."
        ),
        LabTestEntity(
            testName = "Trombosit (Platelet)",
            category = "Hematologi Darah Lengkap",
            unit = "/µL",
            normalLow = 150000.0,
            normalHigh = 450000.0,
            interpretationLow = "Trombositopenia. Waspadai DBD, ITP, atau perdarahan spontan (gusi berdarah, bintik merah ptekie).",
            interpretationHigh = "Trombositosis. Tanda inflamasi kronis atau kelainan mieloproliferatif.",
            clinicalSignificance = "Komponen utama pembekuan darah."
        ),
        LabTestEntity(
            testName = "Gula Darah Sewaktu (GDS)",
            category = "Kimia Darah & Metabolik",
            unit = "mg/dL",
            normalLow = 70.0,
            normalHigh = 140.0,
            interpretationLow = "Hipoglikemia (< 70 mg/dL). Gejala: keringat dingin, gemetar, pusing. Segera minum teh manis.",
            interpretationHigh = "Hiperglikemia. Nilai > 200 mg/dL disertai gejala klasik (banyak kencing, haus, lapar) mengarah ke Diabetes.",
            clinicalSignificance = "Pemantauan kadar glukosa darah saat itu juga."
        ),
        LabTestEntity(
            testName = "HbA1c",
            category = "Kimia Darah & Metabolik",
            unit = "%",
            normalLow = 4.0,
            normalHigh = 5.6,
            interpretationLow = "Normal (< 5.7%). Prediabetes: 5.7 - 6.4%.",
            interpretationHigh = "Diabetes (≥ 6.5%). Menunjukkan rata-rata gula darah tidak terkontrol dalam 2-3 bulan terakhir.",
            clinicalSignificance = "Baku emas pemantauan kontrol diabetes jangka panjang."
        ),
        LabTestEntity(
            testName = "Kreatinin Serum",
            category = "Fungsi Ginjal",
            unit = "mg/dL",
            normalLow = 0.7,
            normalHigh = 1.3,
            interpretationLow = "Dapat terjadi pada massa otot sangat rendah atau malnutrisi berat.",
            interpretationHigh = "Gangguan Fungsi Ginjal (Akut/Kronis). Perlu evaluasi eGFR dan hidrasi yang cukup.",
            clinicalSignificance = "Indikator utama kemampuan filtrasi glomerulus ginjal."
        ),
        LabTestEntity(
            testName = "SGOT (AST)",
            category = "Fungsi Hati",
            unit = "U/L",
            normalLow = 0.0,
            normalHigh = 35.0,
            interpretationLow = "Normal.",
            interpretationHigh = "Kerusakan sel hati / otot. Sering meningkat pada hepatitis, konsumsi alkohol, atau perlemakan hati.",
            clinicalSignificance = "Enzim penanda integritas sel hati dan otot jantung."
        ),
        LabTestEntity(
            testName = "SGPT (ALT)",
            category = "Fungsi Hati",
            unit = "U/L",
            normalLow = 0.0,
            normalHigh = 45.0,
            interpretationLow = "Normal.",
            interpretationHigh = "Spesifik untuk peradangan hati (hepatitis virus, obat hepatotoksik, fatty liver).",
            clinicalSignificance = "Enzim paling spesifik untuk mendeteksi inflamasi hati."
        ),
        LabTestEntity(
            testName = "Asam Urat",
            category = "Kimia Darah & Metabolik",
            unit = "mg/dL",
            normalLow = 3.4,
            normalHigh = 7.0,
            interpretationLow = "Jarang memiliki makna klinis signifikan.",
            interpretationHigh = "Hiperurisemia. Risiko radang sendi asam urat (Gout Arthritis) dan batu ginjal asam urat.",
            clinicalSignificance = "Produk sisa metabolisme purin dari makanan."
        ),
        LabTestEntity(
            testName = "Kolesterol Total",
            category = "Profil Lipid",
            unit = "mg/dL",
            normalLow = 0.0,
            normalHigh = 200.0,
            interpretationLow = "Normal.",
            interpretationHigh = "Hiperkolesterolemia. Risiko aterosklerosis dan penyakit jantung koroner meningkat.",
            clinicalSignificance = "Skrining risiko kardiovaskular."
        )
    )

    private val INITIAL_SKIN_REMEDIES = listOf(
        SkinRemedyEntity(
            conditionKeywords = "jerawat acne pimple",
            naturalRemedy = "Cuci muka 2x sehari dengan sabun berformula lembut, hindari memencet jerawat, kompres es batu untuk redakan radang.",
            otcCream = "Krim Asam Salisilat 2% atau Benzoil Peroksida 2.5-5% dioles tipis pada area berjerawat.",
            referralFlag = false
        ),
        SkinRemedyEntity(
            conditionKeywords = "jamur tinea panu kadas kurap",
            naturalRemedy = "Jaga kulit tetap kering dan bersih, ganti pakaian yang lembap/berkeringat, jangan bertukar handuk.",
            otcCream = "Krim Mikonazol 2% atau Klotrimazol 1% dioleskan 2 kali sehari selama 2-4 minggu.",
            referralFlag = false
        ),
        SkinRemedyEntity(
            conditionKeywords = "eksim dermatitis gatal kering",
            naturalRemedy = "Oleskan pelembap tanpa pewangi segera setelah mandi air suam-suam kuku, hindari sabun deterjen keras.",
            otcCream = "Krim Hidrokortison 1% dioles tipis maksimal 7 hari pada area yang meradang gatal.",
            referralFlag = false
        ),
        SkinRemedyEntity(
            conditionKeywords = "luka bakar melepuh burn",
            naturalRemedy = "Alirkan air mengalir suhu ruang selama 15-20 menit. DILARANG mengoles pasta gigi, mentega, atau kecap!",
            otcCream = "Salep Perak Sulfadiazin (Silver Sulfadiazine) atau gel lidah buaya murni.",
            referralFlag = true
        )
    )
}
