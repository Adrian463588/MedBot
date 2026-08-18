package com.medbot.app.domain.agents.tools

data class ToolResult(
    val toolName: String,
    val isSuccess: Boolean,
    val summary: String,
    val data: Map<String, Any> = emptyMap(),
    val errorMessage: String? = null
)

interface LocalMedicalTool {
    val name: String
    val description: String
    suspend fun execute(params: Map<String, Any>): ToolResult
}
