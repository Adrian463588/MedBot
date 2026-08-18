package com.medbot.app.domain.agents.tools

object ToolRegistry {
    private val toolsMap: Map<String, LocalMedicalTool> = listOf(
        UrgencyAssessorTool(),
        ZScoreCalculatorTool(),
        PaediatricDosingTool(),
        SkinAbcdEvaluatorTool(),
        BmiCalculatorTool(),
        DueDateCalculatorTool(),
        LabInterpreterTool()
    ).associateBy { it.name }

    fun getTool(name: String): LocalMedicalTool? = toolsMap[name]

    suspend fun executeTool(name: String, params: Map<String, Any>): ToolResult {
        val tool = toolsMap[name]
            ?: return ToolResult(
                toolName = name,
                isSuccess = false,
                summary = "Tool tidak ditemukan: $name",
                errorMessage = "Not found",
                status = ToolResultStatus.FAILED
            )
        return try {
            tool.execute(params)
        } catch (e: Exception) {
            ToolResult(
                toolName = name,
                isSuccess = false,
                summary = "Gagal menjalankan $name: ${e.message}",
                errorMessage = e.message,
                status = ToolResultStatus.FAILED
            )
        }
    }
}
