package com.medbot.app.domain.util

import com.medbot.app.domain.model.Drug
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object DrugSimilarityMatcher {

    /**
     * Searches and ranks drugs using a hybrid matching pipeline:
     * 1. Exact substring and prefix match
     * 2. Phonetic & transliteration equivalence (y<->i, ph<->f, x<->ks, c<->s/k, etc.)
     * 3. Character N-Gram subword embedding cosine/Dice similarity
     * 4. Damerau-Levenshtein edit distance ratio
     */
    fun searchAndRank(query: String, drugs: List<Drug>, maxResults: Int = 150): List<Drug> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isBlank()) return drugs.take(maxResults)

        val phoneticQuery = normalizePhonetic(trimmed)
        val queryNgrams = generateNgrams(trimmed)
        val queryWords = trimmed.split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }

        val scoredList = ArrayList<Pair<Drug, Double>>(drugs.size)

        for (drug in drugs) {
            val nameLower = drug.name.lowercase()
            val genericLower = drug.genericName.lowercase()
            val categoryLower = drug.category.lowercase()
            val dosageLower = drug.dosageForm.lowercase()
            val strengthLower = drug.strength.lowercase()

            val scoreName = calculateTargetScore(trimmed, phoneticQuery, queryNgrams, queryWords, nameLower)
            val scoreGeneric = if (genericLower.isNotBlank() && genericLower != nameLower) {
                calculateTargetScore(trimmed, phoneticQuery, queryNgrams, queryWords, genericLower)
            } else 0.0

            val scoreCategory = if (categoryLower.contains(trimmed)) 0.7 else 0.0
            val scoreDosage = if (dosageLower.contains(trimmed) || strengthLower.contains(trimmed)) 0.6 else 0.0

            val finalScore = max(max(scoreName, scoreGeneric * 0.98), max(scoreCategory, scoreDosage))

            if (finalScore >= 0.42) {
                scoredList.add(Pair(drug, finalScore))
            }
        }

        // Sort descending by score, then ascending by name
        scoredList.sortWith { a, b ->
            val scoreCmp = b.second.compareTo(a.second)
            if (scoreCmp != 0) scoreCmp else a.first.name.compareTo(b.first.name, ignoreCase = true)
        }

        return scoredList.take(maxResults).map { it.first }
    }

    private fun calculateTargetScore(
        query: String,
        phoneticQuery: String,
        queryNgrams: Set<String>,
        queryWords: List<String>,
        target: String
    ): Double {
        if (target.isBlank()) return 0.0

        // 1. Direct exact prefix or substring match
        if (target == query) return 1.0
        if (target.startsWith(query)) return 0.98
        if (target.contains(query)) return 0.92

        val phoneticTarget = normalizePhonetic(target)
        if (phoneticTarget == phoneticQuery) return 0.95
        if (phoneticTarget.startsWith(phoneticQuery)) return 0.93
        if (phoneticTarget.contains(phoneticQuery)) return 0.88

        // 2. Token-level matching (e.g. "Ciprofloxacin 500mg" split into "ciprofloxacin", "500mg")
        val targetTokens = target.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
        var maxTokenScore = 0.0

        for (token in targetTokens) {
            if (token == query) return 0.96
            if (token.startsWith(query)) {
                maxTokenScore = max(maxTokenScore, 0.90)
                continue
            }

            val phoneticToken = normalizePhonetic(token)
            if (phoneticToken == phoneticQuery) {
                maxTokenScore = max(maxTokenScore, 0.92)
                continue
            }
            if (phoneticToken.startsWith(phoneticQuery)) {
                maxTokenScore = max(maxTokenScore, 0.86)
                continue
            }

            // Word-level edit distance ratio
            val levSim = damerauLevenshteinSimilarity(query, token)
            if (levSim >= 0.70) {
                maxTokenScore = max(maxTokenScore, levSim * 0.88)
            }

            val phonLevSim = damerauLevenshteinSimilarity(phoneticQuery, phoneticToken)
            if (phonLevSim >= 0.70) {
                maxTokenScore = max(maxTokenScore, phonLevSim * 0.87)
            }
        }

        // 3. Multi-word query matching (e.g. "cipro tablet")
        if (queryWords.size > 1) {
            var matchedWords = 0
            for (qw in queryWords) {
                if (targetTokens.any { it.contains(qw) || normalizePhonetic(it).contains(normalizePhonetic(qw)) }) {
                    matchedWords++
                }
            }
            val wordMatchRatio = matchedWords.toDouble() / queryWords.size
            if (wordMatchRatio >= 0.6) {
                maxTokenScore = max(maxTokenScore, wordMatchRatio * 0.85)
            }
        }

        // 4. Character N-Gram Subword Vector / Dice Similarity
        val targetNgrams = generateNgrams(target)
        val ngramSim = computeNgramDice(queryNgrams, targetNgrams)

        return max(maxTokenScore, ngramSim * 0.82)
    }

    /**
     * Normalizes text phonetically for pharmaceutical / chemical terms.
     * E.g. cyprofloxacin -> siprofloksasin, parasetamol -> parasetamol
     */
    fun normalizePhonetic(input: String): String {
        var s = input.lowercase().trim()
        if (s.isEmpty()) return ""

        // Multi-character replacements
        s = s.replace("ph", "f")
        s = s.replace("th", "t")
        s = s.replace("ch", "k")
        s = s.replace("tion", "si")
        s = s.replace("cion", "si")
        s = s.replace("x", "ks")

        val sb = StringBuilder(s.length)
        for (i in s.indices) {
            val c = s[i]
            when (c) {
                'y' -> sb.append('i')
                'z' -> sb.append('s')
                'v' -> sb.append('f')
                'c' -> {
                    // 'c' followed by e, i, y -> 's', else -> 'k'
                    if (i + 1 < s.length && (s[i + 1] == 'e' || s[i + 1] == 'i' || s[i + 1] == 'y')) {
                        sb.append('s')
                    } else {
                        sb.append('k')
                    }
                }
                else -> {
                    if (c in 'a'..'z' || c in '0'..'9') {
                        sb.append(c)
                    }
                }
            }
        }

        // Deduplicate consecutive letters (e.g. 'll' -> 'l', 'mm' -> 'm', 'xx' -> 'x')
        val dedup = StringBuilder(sb.length)
        var lastChar = ' '
        for (i in 0 until sb.length) {
            val c = sb[i]
            if (c != lastChar) {
                dedup.append(c)
                lastChar = c
            }
        }

        return dedup.toString()
    }

    /**
     * Generates character 2-grams and 3-grams with boundary markers.
     */
    private fun generateNgrams(text: String): Set<String> {
        val padded = "^$text$"
        val ngrams = HashSet<String>(padded.length * 2)
        // Bigrams
        for (i in 0 until padded.length - 1) {
            ngrams.add(padded.substring(i, i + 2))
        }
        // Trigrams
        for (i in 0 until padded.length - 2) {
            ngrams.add(padded.substring(i, i + 3))
        }
        return ngrams
    }

    /**
     * Computes Dice coefficient similarity between two subword n-gram sets.
     */
    private fun computeNgramDice(setA: Set<String>, setB: Set<String>): Double {
        if (setA.isEmpty() || setB.isEmpty()) return 0.0
        var intersection = 0
        for (gram in setA) {
            if (setB.contains(gram)) {
                intersection++
            }
        }
        return (2.0 * intersection) / (setA.size + setB.size)
    }

    /**
     * Damerau-Levenshtein Distance with transpositions.
     * Returns normalized similarity in range [0.0, 1.0].
     */
    fun damerauLevenshteinSimilarity(s1: String, s2: String): Double {
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0 && len2 == 0) return 1.0
        if (len1 == 0 || len2 == 0) return 0.0

        val maxLen = max(len1, len2)
        val d = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) d[i][0] = i
        for (j in 0..len2) d[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                var minVal = min(d[i - 1][j] + 1, min(d[i][j - 1] + 1, d[i - 1][j - 1] + cost))

                // Transposition
                if (i > 1 && j > 1 && s1[i - 1] == s2[j - 2] && s1[i - 2] == s2[j - 1]) {
                    minVal = min(minVal, d[i - 2][j - 2] + cost)
                }
                d[i][j] = minVal
            }
        }

        val distance = d[len1][len2]
        return max(0.0, 1.0 - (distance.toDouble() / maxLen))
    }
}
