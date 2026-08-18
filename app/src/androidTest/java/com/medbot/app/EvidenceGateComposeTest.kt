package com.medbot.app

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Compose-only evidence gate. It checks presence of supplied inputs, not model output.
 */
@RunWith(AndroidJUnit4::class)
class EvidenceGateComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun missing_real_model_document_and_photo_render_blocked_state() {
        val checks = EvidenceGate.inspect(
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            modelPath = null,
            documentUri = null,
            photoUri = null
        )

        assertTrue(checks.all { it.status == EvidenceStatus.BLOCKED })
        composeRule.setContent {
            MaterialTheme {
                EvidenceGateSummary(checks)
            }
        }

        composeRule.onNodeWithText("Evidence gate: BLOCKED").assertIsDisplayed()
        composeRule.onNodeWithText("No real model supplied", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("No real document supplied", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("No real photo supplied", substring = true).assertIsDisplayed()
    }
}

private enum class EvidenceStatus {
    AVAILABLE,
    BLOCKED
}

private data class EvidenceCheck(
    val label: String,
    val status: EvidenceStatus,
    val reason: String
)

private object EvidenceGate {

    fun inspect(
        context: Context,
        modelPath: String?,
        documentUri: String?,
        photoUri: String?
    ): List<EvidenceCheck> = listOf(
        checkModel(modelPath),
        checkUri(context, "document", documentUri),
        checkUri(context, "photo", photoUri)
    )

    private fun checkModel(path: String?): EvidenceCheck {
        if (path.isNullOrBlank()) {
            return EvidenceCheck("model", EvidenceStatus.BLOCKED, "No real model supplied")
        }
        val file = File(path)
        val supported = path.endsWith(".litertlm", ignoreCase = true)
        return if (supported && file.isFile && file.length() > 0) {
            EvidenceCheck("model", EvidenceStatus.AVAILABLE, "Readable model file supplied")
        } else {
            EvidenceCheck("model", EvidenceStatus.BLOCKED, "Model path is missing, empty, or unsupported")
        }
    }

    private fun checkUri(context: Context, label: String, rawUri: String?): EvidenceCheck {
        if (rawUri.isNullOrBlank()) {
            return EvidenceCheck(label, EvidenceStatus.BLOCKED, "No real $label supplied")
        }
        return try {
            val readable = context.contentResolver.openAssetFileDescriptor(Uri.parse(rawUri), "r")?.use {
                it.length != 0L
            } == true
            if (readable) {
                EvidenceCheck(label, EvidenceStatus.AVAILABLE, "Readable content URI supplied")
            } else {
                EvidenceCheck(label, EvidenceStatus.BLOCKED, "Content URI is empty or unreadable")
            }
        } catch (error: Exception) {
            EvidenceCheck(label, EvidenceStatus.BLOCKED, "Content URI is unavailable: ${error.javaClass.simpleName}")
        }
    }
}

@Composable
private fun EvidenceGateSummary(checks: List<EvidenceCheck>) {
    val blocked = checks.filter { it.status == EvidenceStatus.BLOCKED }
    Column(modifier = Modifier.padding(16.dp)) {
        Text(if (blocked.isEmpty()) "Evidence gate: AVAILABLE" else "Evidence gate: BLOCKED")
        blocked.forEach { check ->
            Text("${check.label}: ${check.reason}")
        }
    }
}
