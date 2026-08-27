package com.medbot.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.medbot.app.data.rag.BundledKnowledgeSeeder
import com.medbot.app.data.rag.RagClinicalEvidenceRepository
import com.medbot.app.data.rag.RagOrchestrator
import com.medbot.app.data.rag.BundledKnowledgeState
import com.medbot.app.data.rag.LocalEmbedder
import com.medbot.app.data.repository.RagRepositoryImpl
import com.medbot.app.data.local.database.MedBotDatabase
import com.medbot.app.domain.model.EvidenceQuery
import com.medbot.app.domain.model.EvidenceResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves that the bundled corpus and the real Android MiniLM runtime can
 * produce typed evidence. No production database or test repository double is
 * used; the Room database is isolated in memory for the instrumented run.
 */
@RunWith(AndroidJUnit4::class)
class BankBookRagRuntimeInstrumentedTest {

    @Test
    fun realBankBookCorpusRetrievesDiareEvidence() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, MedBotDatabase::class.java).build()
        val embedder = LocalEmbedder(context)
        val orchestrator = RagOrchestrator(database.ragDao(), embedder = embedder)
        val seeder = BundledKnowledgeSeeder(context, orchestrator)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            seeder.start(scope)
            val terminal = withTimeout(45_000L) {
                seeder.state.first { state ->
                    state is BundledKnowledgeState.Ready || state is BundledKnowledgeState.Failed
                }
            }
            assertTrue("Bundled corpus indexing failed: $terminal", terminal is BundledKnowledgeState.Ready)

            val repository = RagClinicalEvidenceRepository(
                RagRepositoryImpl(orchestrator, seeder)
            )
            val result = repository.retrieve(
                EvidenceQuery(
                    text = "Saya mengalami diare, bagaimana triase dan penatalaksanaannya?",
                    medicationRequest = false,
                    topK = 5
                )
            )

            assertTrue("Expected typed local evidence, got $result", result is EvidenceResult.Ready)
            val evidence = (result as EvidenceResult.Ready).evidence
            assertTrue(evidence.any { item ->
                item.title.contains("diare", ignoreCase = true) ||
                    item.text.contains("diare", ignoreCase = true)
            })
            assertTrue(evidence.all { item -> item.evidenceId.isNotBlank() && item.sourceSha256?.length == 64 })

            val medicationResult = repository.retrieve(
                EvidenceQuery(
                    text = "Obat apa yang sesuai untuk diare dan apa kontraindikasinya?",
                    medicationRequest = true,
                    topK = 5
                )
            )
            assertTrue("Expected medication-eligible evidence, got $medicationResult", medicationResult is EvidenceResult.Ready)
            assertTrue(
                "Retrieved evidence did not pass the medication gate",
                (medicationResult as EvidenceResult.Ready).evidence.any { it.medicationEligible }
            )
        } finally {
            scope.cancel()
            embedder.close()
            database.close()
        }
    }
}
