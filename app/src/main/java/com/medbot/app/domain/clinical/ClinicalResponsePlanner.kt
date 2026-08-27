package com.medbot.app.domain.clinical

import com.medbot.app.domain.model.AppLanguage
import com.medbot.app.domain.model.ClinicalEvidence
import com.medbot.app.domain.model.ClinicalEvidenceKind
import com.medbot.app.domain.model.OrchestratorResult
import com.medbot.app.domain.model.SearchResult

/**
 * Builds a deterministic clinical conversation contract.
 *
 * The planner never fills patient facts, diagnoses, or prescriptions. It only
 * decides which questions the local model must ask and which source sections it
 * may summarize before a clinician evaluates the patient.
 */
class ClinicalResponsePlanner {
    fun plan(
        query: String,
        triage: OrchestratorResult,
        evidenceText: String,
        language: AppLanguage,
        evidence: List<ClinicalEvidence> = emptyList()
    ): ClinicalResponsePlan {
        val normalized = query.lowercase()
        val topic = detectTopic(normalized)
        val typedEvidence = evidence
        val evidenceTextNormalized = evidenceText.lowercase()
        val hasAuthoritativeSource = listOf(
            "[ppk-fktp", "[kapita selekta", "[iso indonesia", "[mims indonesia",
            "[who ", "official clinical guideline", "national clinical guideline"
        ).any(evidenceTextNormalized::contains)
        val explicitlyRestricted = evidenceTextNormalized.contains("classification_only") ||
            evidenceTextNormalized.contains("secondary_web_education") || evidenceTextNormalized.contains("education_only")

        val hasTypedTreatmentEvidence = typedEvidence.isNotEmpty() && typedEvidence.any {
            it.evidenceKind in setOf(
                ClinicalEvidenceKind.GUIDELINE,
                ClinicalEvidenceKind.DRUG_MONOGRAPH,
                ClinicalEvidenceKind.DRUG_INTERACTION,
                ClinicalEvidenceKind.COMPOUNDING_PROTOCOL,
                ClinicalEvidenceKind.CLINICAL_REFERENCE
            )
        }
        val hasTreatmentEvidence = if (typedEvidence.isNotEmpty()) {
            hasTypedTreatmentEvidence
        } else {
            (!explicitlyRestricted || hasAuthoritativeSource) && listOf(
                "penatalaksanaan", "tatalaksana", "terapi", "oralit", "rehidrasi",
                "zink", "zinc", "monograf", "indikasi", "protocol", "protokol"
            ).any(evidenceTextNormalized::contains)
        }

        val questions = if (topic == ClinicalTopic.DIARRHOEA) {
            if (language == AppLanguage.ENGLISH) {
                listOf(
                    "What is the patient's age, and weight if the patient is a child?",
                    "When did it start, how many stools occurred, and is there blood or mucus?",
                    "Are there persistent vomiting, fever, severe abdominal pain, seizure, or unusual sleepiness?",
                    "Can the patient drink, and when was the last urination? Are there dry mouth or sunken eyes?",
                    "Is the patient pregnant, immunocompromised, chronically ill, allergic to a medicine, or taking a new medicine/antibiotic?"
                )
            } else {
                listOf(
                    "Berapa usia pasien dan berapa berat badannya bila pasien anak?",
                    "Sejak kapan, berapa kali BAB, dan apakah ada darah atau lendir?",
                    "Apakah ada muntah terus-menerus, demam, nyeri perut hebat, kejang, atau sangat mengantuk/lemas?",
                    "Apakah pasien masih bisa minum dan kapan terakhir BAK? Apakah mulut kering atau mata cekung?",
                    "Apakah hamil, daya tahan tubuh rendah, memiliki penyakit kronis, alergi obat, atau baru minum obat/antibiotik?"
                )
            }
        } else {
            generalQuestions(topic, language)
        }

        val missingContext = if (language == AppLanguage.ENGLISH) {
            listOf("patient age", "symptom duration/severity", "red-flag answers", "medication/allergy history")
        } else {
            listOf("usia pasien", "lama/derajat gejala", "jawaban tanda bahaya", "riwayat obat/alergi")
        }

        return ClinicalResponsePlan(
            topic = topic.wireName,
            urgency = triage.urgency.name,
            urgencyReason = triage.reasoning,
            probingQuestions = questions,
            missingContext = missingContext,
            hasTreatmentEvidence = hasTreatmentEvidence,
            language = language
        )
    }

    private fun generalQuestions(topic: ClinicalTopic, language: AppLanguage): List<String> {
        val english = language == AppLanguage.ENGLISH
        val symptomSpecific = when (topic) {
            ClinicalTopic.DIARRHOEA -> if (english) {
                "How long has the diarrhoea lasted, how frequent are the stools, and can the patient drink?"
            } else {
                "Sejak kapan diare berlangsung, berapa sering BAB, dan apakah pasien masih dapat minum?"
            }
            ClinicalTopic.FEVER -> if (english) {
                "What is the measured temperature, how long has the fever lasted, and are there chills or a rash?"
            } else {
                "Berapa suhu yang terukur, sejak kapan demam berlangsung, dan apakah ada menggigil atau ruam?"
            }
            ClinicalTopic.COUGH -> if (english) {
                "How long has the cough lasted, is there breathlessness, chest pain, blood, or measured oxygen saturation?"
            } else {
                "Sejak kapan batuk berlangsung, apakah ada sesak, nyeri dada, darah, atau saturasi oksigen terukur?"
            }
            ClinicalTopic.VOMITING -> if (english) {
                "How often is vomiting occurring, can the patient keep fluids down, and when was the last urination?"
            } else {
                "Berapa kali muntah, apakah cairan dapat dipertahankan, dan kapan terakhir buang air kecil?"
            }
            ClinicalTopic.PAIN -> if (english) {
                "Where is the pain, when did it start, how severe is it, and what makes it better or worse?"
            } else {
                "Di mana nyerinya, kapan mulai, seberapa berat, dan apa yang memperbaiki atau memperburuknya?"
            }
            ClinicalTopic.RASH -> if (english) {
                "When did the rash start, where did it begin, is it painful or itchy, and are there mouth or eye lesions?"
            } else {
                "Kapan ruam mulai, dari mana bermula, apakah nyeri atau gatal, dan apakah ada lesi di mulut atau mata?"
            }
            ClinicalTopic.GENERAL -> if (english) {
                "What symptom is most concerning, when did it start, how has it changed, and what has already been tried?"
            } else {
                "Gejala apa yang paling mengkhawatirkan, kapan mulai, bagaimana perubahannya, dan apa yang sudah dicoba?"
            }
        }
        return if (english) {
            listOf(
                "What is the patient's age, symptom onset, duration, and progression?",
                symptomSpecific,
                "Are there red flags, pregnancy, chronic disease, allergies, or current medicines?"
            )
        } else {
            listOf(
                "Berapa usia pasien, kapan gejala mulai, berapa lama, dan apakah memburuk?",
                symptomSpecific,
                "Apakah ada tanda bahaya, kehamilan, penyakit kronis, alergi, atau obat yang sedang diminum?"
            )
        }
    }

    private fun detectTopic(query: String): ClinicalTopic = when {
        query.containsAny("diare", "diarrhea", "diarrhoea", "mencret") -> ClinicalTopic.DIARRHOEA
        query.containsAny("demam", "fever", "febris") -> ClinicalTopic.FEVER
        query.containsAny("batuk", "cough") -> ClinicalTopic.COUGH
        query.containsAny("muntah", "vomit", "vomiting", "emesis") -> ClinicalTopic.VOMITING
        query.containsAny("nyeri", "pain", "sakit") -> ClinicalTopic.PAIN
        query.containsAny("ruam", "rash", "bercak") -> ClinicalTopic.RASH
        else -> ClinicalTopic.GENERAL
    }

    private fun String.containsAny(vararg values: String): Boolean = values.any { contains(it) }

    private enum class ClinicalTopic(val wireName: String) {
        DIARRHOEA("diarrhoea"),
        FEVER("fever"),
        COUGH("cough"),
        VOMITING("vomiting"),
        PAIN("pain"),
        RASH("rash"),
        GENERAL("general clinical complaint")
    }
}

data class ClinicalResponsePlan(
    val topic: String,
    val urgency: String,
    val urgencyReason: String,
    val probingQuestions: List<String>,
    val missingContext: List<String>,
    val hasTreatmentEvidence: Boolean,
    val language: AppLanguage
) {
    /** Prompt-only contract; every item is explicitly marked as non-patient data. */
    fun toPromptBlock(): String {
        val english = language == AppLanguage.ENGLISH
        val heading = if (english) "CLINICAL RESPONSE PLAN" else "RENCANA RESPONS KLINIS"
        val triageLabel = if (english) "Preliminary triage" else "Triase awal"
        val reasonLabel = if (english) "Routing reason" else "Alasan perutean"
        val missingLabel = if (english) "Still missing; ask before individual advice" else "Masih kurang; tanyakan sebelum saran individual"
        val probingLabel = if (english) "Mandatory probing questions" else "Pertanyaan probing wajib"
        val diagnosisRule = if (english) {
            "Diagnosis: do not assert a diagnosis. Present only source-supported differential directions and what examination/test would distinguish them."
        } else {
            "Diagnosis: jangan menegakkan diagnosis. Tampilkan hanya arah diagnosis banding yang didukung sumber dan pemeriksaan/tes yang dapat membedakannya."
        }
        val medicationRule = if (english) {
            "Medication: summarize only an indication, contraindication, dose, duration, or protocol that is explicitly present in the cited source and applies to the stated population. Never turn it into an individual prescription or compounding formula."
        } else {
            "Obat: ringkas hanya indikasi, kontraindikasi, dosis, durasi, atau protokol yang tertulis eksplisit pada sumber yang disitasi dan berlaku untuk populasi yang disebutkan. Jangan mengubahnya menjadi resep individual atau formula racikan."
        }
        val evidenceRule = if (english) {
            "Evidence status: the app has treatment evidence=$hasTreatmentEvidence. If a required fact is absent or sources conflict, say INSUFFICIENT_DATA and name the missing source/fact."
        } else {
            "Status evidence: aplikasi memiliki evidence penatalaksanaan=$hasTreatmentEvidence. Jika fakta yang dibutuhkan tidak ada atau sumber berbeda, tulis INSUFFICIENT_DATA dan sebutkan fakta/sumber yang kurang."
        }
        val outputLabel = if (english) "Required answer order" else "Urutan jawaban wajib"
        val outputItems = if (english) {
            listOf(
                "1. Triage and immediate safety action (not a diagnosis).",
                "2. Probing questions and the facts still needed.",
                "3. Source-supported differential directions and limits.",
                "4. Evidence-based supportive care and medication facts, with population limits.",
                "5. Red flags, escalation timeframe, and citations."
            )
        } else {
            listOf(
                "1. Triase dan tindakan keselamatan segera (bukan diagnosis).",
                "2. Pertanyaan probing dan data yang masih diperlukan.",
                "3. Arah diagnosis banding yang didukung sumber beserta batasannya.",
                "4. Perawatan suportif berbasis evidence dan fakta obat, dengan batas populasi.",
                "5. Tanda bahaya, batas waktu mencari pertolongan, dan sitasi."
            )
        }
        return buildString {
            append("[$heading — ROUTING ONLY, NOT PATIENT FACTS]\n")
            append("$triageLabel: $urgency\n")
            append("$reasonLabel: ${urgencyReason.take(500)}\n")
            append("$missingLabel: ${missingContext.joinToString(", ")}\n")
            append("$probingLabel:\n")
            probingQuestions.forEach { append("- $it\n") }
            append("$diagnosisRule\n")
            append("$medicationRule\n")
            append("$evidenceRule\n")
            append("$outputLabel:\n")
            outputItems.forEach { append("$it\n") }
        }.trim()
    }

    /** Adds app-owned probing context without inventing an answer or patient fact. */
    fun appendProbingContext(response: String): String {
        val normalized = response.lowercase()
        val hasProbingHeading = normalized.contains("probing") ||
            normalized.contains("anamnesis") ||
            normalized.contains("pertanyaan penting") ||
            normalized.contains("probing questions")
        val hasTriageHeading = normalized.contains("triase") || normalized.contains("triage")

        val heading = if (language == AppLanguage.ENGLISH) {
            "\n\nQuestions needed for safe triage"
        } else {
            "\n\nPertanyaan untuk triase yang aman"
        }
        return buildString {
            append(response.trim())
            if (!hasTriageHeading) {
                append(
                    if (language == AppLanguage.ENGLISH) {
                        "\n\nPreliminary triage (not a diagnosis): $urgency"
                    } else {
                        "\n\nTriase awal (bukan diagnosis): $urgency"
                    }
                )
            }
            if (hasProbingHeading) return@buildString
            append(heading)
            append(":\n")
            probingQuestions.forEach { append("- $it\n") }
            append(
                if (language == AppLanguage.ENGLISH) {
                    "This routing context is not a diagnosis or an individual prescription."
                } else {
                    "Konteks perutean ini bukan diagnosis atau resep individual."
                }
            )
        }.trim()
    }
}

/**
 * Selects source chunks for a clinical question. Title/topic agreement is
 * required so a generic mention of a symptom in an unrelated monograph cannot
 * make the answer look grounded.
 */
object ClinicalEvidenceSelector {
    fun select(query: String, results: List<SearchResult>, maxResults: Int = 5): List<SearchResult> {
        return results
            .filter { isRelevant(query, it) }
            .sortedWith(
                compareByDescending<SearchResult> { titleOverlap(query, it.documentTitle) }
                    .thenByDescending { clinicalSectionOverlap(it.chunk.textContent) }
                    .thenByDescending { it.similarityScore }
            )
            .distinctBy { it.chunk.id }
            .take(maxResults)
    }

    fun hasRelevantEvidence(query: String, results: List<SearchResult>): Boolean =
        results.any { isRelevant(query, it) }

    private fun isRelevant(query: String, result: SearchResult): Boolean {
        val queryTerms = tokenize(query).filterNot { it in GENERIC_TERMS }.toSet()
        if (queryTerms.isEmpty()) return result.similarityScore >= HIGH_SEMANTIC_THRESHOLD
        val titleMatch = queryTerms.intersect(tokenize(result.documentTitle)).size
        val textMatch = queryTerms.intersect(tokenize(result.chunk.textContent)).size
        val clinicalText = clinicalSectionOverlap(result.chunk.textContent) > 0
        return clinicalText && (
            titleMatch > 0 ||
                textMatch >= MIN_TEXT_MATCHES ||
                (textMatch > 0 && result.similarityScore >= HIGH_SEMANTIC_THRESHOLD)
            )
    }

    private fun titleOverlap(query: String, title: String): Int =
        tokenize(query).intersect(tokenize(title)).size

    private fun clinicalSectionOverlap(text: String): Int =
        CLINICAL_SECTION_TERMS.count { text.contains(it, ignoreCase = true) }

    private fun tokenize(text: String): Set<String> = text
        .lowercase()
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length >= 3 && it !in GENERIC_TERMS }
        .toSet()

    private const val MIN_TEXT_MATCHES = 2
    private const val HIGH_SEMANTIC_THRESHOLD = 0.60f
    private val GENERIC_TERMS = setOf(
        "yang", "dan", "atau", "untuk", "dengan", "dari", "pada", "dalam", "tidak",
        "ada", "apa", "saya", "ini", "itu", "akan", "bisa", "harus", "lebih", "sakit",
        "bagaimana", "penanganan", "pengobatan", "obat", "obatnya", "resep", "dosis",
        "the", "and", "or", "for", "with", "from", "this", "that", "what", "how", "can"
    )
    private val CLINICAL_SECTION_TERMS = listOf(
        "anamnesis", "diagnosis", "penatalaksanaan", "tatalaksana", "terapi",
        "tanda bahaya", "red flags", "warning signs", "pedoman", "gejala",
        "indikasi", "kontraindikasi", "rehidrasi", "oralit", "zink", "zinc",
        "antibiotik", "antidiare", "dosis", "monograf"
    )
}
