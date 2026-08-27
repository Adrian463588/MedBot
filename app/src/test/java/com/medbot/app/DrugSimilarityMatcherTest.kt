package com.medbot.app

import com.medbot.app.domain.model.Drug
import com.medbot.app.domain.util.DrugSimilarityMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrugSimilarityMatcherTest {

    private val sampleDrugs = listOf(
        Drug(
            name = "Ciprofloxacin 500 mg",
            genericName = "Ciprofloxacin",
            category = "Antibiotik",
            indication = "Antibiotik spektrum luas",
            dosageForm = "Tablet",
            strength = "500 mg"
        ),
        Drug(
            name = "Paracetamol 500 mg",
            genericName = "Paracetamol",
            category = "Analgesik & Antipiretik",
            indication = "Meredakan demam dan nyeri",
            dosageForm = "Tablet",
            strength = "500 mg"
        ),
        Drug(
            name = "Amoxicillin Trihydrate 500 mg",
            genericName = "Amoxicillin",
            category = "Antibiotik",
            indication = "Antibiotik golongan penisilin",
            dosageForm = "Kapsul",
            strength = "500 mg"
        ),
        Drug(
            name = "Ibuprofen 400 mg",
            genericName = "Ibuprofen",
            category = "Analgesik & Antiinflamasi",
            indication = "Meredakan peradangan dan nyeri",
            dosageForm = "Tablet",
            strength = "400 mg"
        ),
        Drug(
            name = "Omeprazole 20 mg",
            genericName = "Omeprazole",
            category = "Antasida & Antirefluks",
            indication = "Menurunkan asam lambung",
            dosageForm = "Kapsul",
            strength = "20 mg"
        ),
        Drug(
            name = "Cetirizine HCl 10 mg",
            genericName = "Cetirizine",
            category = "Antihistamin & Antialergi",
            indication = "Mengatasi gejala alergi",
            dosageForm = "Tablet",
            strength = "10 mg"
        )
    )

    @Test
    fun testTypoCyprofloxacinMatchesCiprofloxacin() {
        val results = DrugSimilarityMatcher.searchAndRank("cyprofloxacin", sampleDrugs)
        assertFalse("Results should not be empty for typo 'cyprofloxacin'", results.isEmpty())
        assertEquals("Ciprofloxacin 500 mg", results.first().name)
    }

    @Test
    fun testTypoParasetamolMatchesParacetamol() {
        val results = DrugSimilarityMatcher.searchAndRank("parasetamol", sampleDrugs)
        assertFalse("Results should not be empty for typo 'parasetamol'", results.isEmpty())
        assertEquals("Paracetamol 500 mg", results.first().name)
    }

    @Test
    fun testTypoAmoxilinMatchesAmoxicillin() {
        val results = DrugSimilarityMatcher.searchAndRank("amoxilin", sampleDrugs)
        assertFalse("Results should not be empty for typo 'amoxilin'", results.isEmpty())
        assertEquals("Amoxicillin Trihydrate 500 mg", results.first().name)
    }

    @Test
    fun testTypoIbupropenMatchesIbuprofen() {
        val results = DrugSimilarityMatcher.searchAndRank("ibupropen", sampleDrugs)
        assertFalse("Results should not be empty for typo 'ibupropen'", results.isEmpty())
        assertEquals("Ibuprofen 400 mg", results.first().name)
    }

    @Test
    fun testTypoOmeprazolMatchesOmeprazole() {
        val results = DrugSimilarityMatcher.searchAndRank("omeprazol", sampleDrugs)
        assertFalse("Results should not be empty for typo 'omeprazol'", results.isEmpty())
        assertEquals("Omeprazole 20 mg", results.first().name)
    }

    @Test
    fun testPhoneticNormalization() {
        assertEquals("siprofloksasin", DrugSimilarityMatcher.normalizePhonetic("cyprofloxacin"))
        assertEquals("siprofloksasin", DrugSimilarityMatcher.normalizePhonetic("ciprofloxacin"))
        assertEquals("parasetamol", DrugSimilarityMatcher.normalizePhonetic("paracetamol"))
        assertEquals("parasetamol", DrugSimilarityMatcher.normalizePhonetic("parasetamol"))
    }
}
