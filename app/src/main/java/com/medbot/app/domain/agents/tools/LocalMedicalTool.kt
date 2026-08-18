package com.medbot.app.domain.agents.tools

enum class ToolResultStatus {
    SUCCESS,
    VALIDATION_ERROR,
    INSUFFICIENT_DATA,
    FAILED
}

data class ToolResult(
    val toolName: String,
    val isSuccess: Boolean,
    val summary: String,
    val data: Map<String, Any> = emptyMap(),
    val errorMessage: String? = null,
    val status: ToolResultStatus = if (isSuccess) {
        ToolResultStatus.SUCCESS
    } else {
        ToolResultStatus.FAILED
    }
)

interface LocalMedicalTool {
    val name: String
    val description: String
    suspend fun execute(params: Map<String, Any>): ToolResult
}
