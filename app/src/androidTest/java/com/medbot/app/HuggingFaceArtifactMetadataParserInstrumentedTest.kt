package com.medbot.app

import com.medbot.app.data.platform.HuggingFaceArtifactMetadataParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses Android's org.json implementation; local JVM tests intentionally do not mock it. */
class HuggingFaceArtifactMetadataParserInstrumentedTest {
    @Test
    fun acceptsExactAuthenticatedArtifactMetadata() {
        val revision = "9bcaf1a255db7a73120b1ff6baa5015512569cd2"
        val sha = "a".repeat(64)
        val result = HuggingFaceArtifactMetadataParser.parse(
            responseBody = """
                [{"path":"other.litertlm","size":1},
                 {"path":"model.litertlm","size":3023069488,"lfs":{"oid":"$sha"},"lastCommit":{"id":"$revision"}}]
            """.trimIndent(),
            fileName = "model.litertlm",
            expectedSizeBytes = 3023069488L,
            expectedSourceRevision = revision
        )

        assertTrue(result.isSuccess)
        assertEquals(sha, result.getOrThrow().sha256)
    }

    @Test
    fun rejectsMaskedOrMissingLfsSha() {
        val result = HuggingFaceArtifactMetadataParser.parse(
            responseBody = "[{\"path\":\"model.litertlm\",\"size\":3023069488,\"lfs\":{\"oid\":\"********************************\"}}]",
            fileName = "model.litertlm",
            expectedSizeBytes = 3023069488L,
            expectedSourceRevision = "revision"
        )

        assertTrue(result.isFailure)
    }
}
