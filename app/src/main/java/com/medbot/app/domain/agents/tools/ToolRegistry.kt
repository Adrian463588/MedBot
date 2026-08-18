package com.medbot.app.domain.agents.tools

object ToolRegistry {
    private val toolsMap: Map<String, LocalMedicalTool> = listOf(
        UrgencyAssessorTool(),
        ZScoreCalculatorTool(),
        PaediatricDosingTool(),
        SkinAbcdEvaluatorTool(),
        BmiCalculatorTool(),
        DueDateCalculatorTool(),
        LabInterpreterTool(),
        CapabilityUnavailableTool("get_drug_info", "Database obat terverifikasi belum tersedia di perangkat."),
        CapabilityUnavailableTool("manage_chronic_disease", "Panduan penyakit kronis tervalidasi belum tersedia di perangkat."),
        CapabilityUnavailableTool("check_drug_interaction", "Database interaksi obat terverifikasi belum tersedia di perangkat."),
        CapabilityUnavailableTool("search_skin_remedy", "Database perawatan kulit terverifikasi belum tersedia di perangkat.")
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

/**
 * Keeps agent/tool contracts explicit while a required local data source is
 * absent. It never returns a clinical value, recommendation, or success.
 */
private class CapabilityUnavailableTool(
    override val name: String,
    private val unavailableReason: String
) : LocalMedicalTool {
    override val description: String = unavailableReason

    override suspend fun execute(params: Map<String, Any>): ToolResult = ToolResult(
        toolName = name,
        isSuccess = false,
        summary = "UNAVAILABLE: $unavailableReason",
        data = mapOf("status" to ToolResultStatus.UNAVAILABLE.name),
        errorMessage = unavailableReason,
        status = ToolResultStatus.UNAVAILABLE
    )
}
