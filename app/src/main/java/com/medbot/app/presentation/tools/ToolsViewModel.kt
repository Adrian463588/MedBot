@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.medbot.app.presentation.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.medbot.app.domain.agents.tools.ToolRegistry
import com.medbot.app.domain.agents.tools.ToolResult
import com.medbot.app.domain.agents.tools.ToolResultStatus
import com.medbot.app.domain.model.Drug
import com.medbot.app.domain.model.DrugInteraction
import com.medbot.app.domain.model.LabTest
import com.medbot.app.domain.model.Reminder
import com.medbot.app.domain.model.ReminderType
import com.medbot.app.domain.repository.DrugRepository
import com.medbot.app.domain.repository.HealthToolsRepository
import com.medbot.app.domain.tools.CalculatorInputValidation
import com.medbot.app.domain.tools.ClinicalCalculatorInputValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface ToolsUiEvent {
    data class SelectTab(val index: Int) : ToolsUiEvent
    data class SetDrugQuery(val query: String) : ToolsUiEvent
    data class CheckDrugInteraction(val drugA: String, val drugB: String) : ToolsUiEvent
    data class EvaluateLab(val testName: String, val value: String) : ToolsUiEvent
    data class CalculatePaediatric(val ageMonths: String, val weightKg: String, val heightCm: String, val gender: String, val drugName: String, val indication: String) : ToolsUiEvent
    data class CalculateBmi(val weightKg: String, val heightCm: String) : ToolsUiEvent
    data class CalculateDueDate(val day: String, val month: String, val year: String) : ToolsUiEvent
    data class ToggleReminder(val id: String, val enabled: Boolean) : ToolsUiEvent
    data class AddReminder(val title: String, val hour: Int, val minute: Int) : ToolsUiEvent
    data class DeleteReminder(val id: String) : ToolsUiEvent
}

data class CalculatorUiState(
    val kind: CalculatorKind? = null,
    val isRunning: Boolean = false,
    val result: List<ToolResult>? = null,
    val error: CalculatorErrorKind? = null
)

enum class CalculatorKind { PAEDIATRIC, BMI, DUE_DATE }
enum class CalculatorErrorKind { VALIDATION, UNAVAILABLE, FAILED }

enum class LabEvaluationKind { INVALID_INPUT, UNAVAILABLE, RESULT }

data class LabEvaluationState(
    val kind: LabEvaluationKind,
    val value: Double? = null,
    val test: LabTest? = null,
    val status: LabComparisonStatus? = null
)

enum class LabComparisonStatus { BELOW_REFERENCE, ABOVE_REFERENCE, WITHIN_REFERENCE }

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val drugRepository: DrugRepository,
    private val healthToolsRepository: HealthToolsRepository
) : ViewModel() {
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()
    private val _drugSearchQuery = MutableStateFlow("")
    val drugSearchQuery: StateFlow<String> = _drugSearchQuery.asStateFlow()
    val searchResults: StateFlow<List<Drug>> = _drugSearchQuery
        .flatMapLatest { query -> drugRepository.searchDrugs(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _checkedInteractions = MutableStateFlow<List<DrugInteraction>>(emptyList())
    val checkedInteractions: StateFlow<List<DrugInteraction>> = _checkedInteractions.asStateFlow()
    val labTests: StateFlow<List<LabTest>> = healthToolsRepository.getLabTests()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val reminders: StateFlow<List<Reminder>> = healthToolsRepository.getReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _calculatorState = MutableStateFlow(CalculatorUiState())
    val calculatorState: StateFlow<CalculatorUiState> = _calculatorState.asStateFlow()
    private val _labEvaluation = MutableStateFlow<LabEvaluationState?>(null)
    val labEvaluation: StateFlow<LabEvaluationState?> = _labEvaluation.asStateFlow()
    private var calculatorJob: Job? = null

    fun onEvent(event: ToolsUiEvent) {
        when (event) {
            is ToolsUiEvent.SelectTab -> selectTab(event.index)
            is ToolsUiEvent.SetDrugQuery -> setDrugQuery(event.query)
            is ToolsUiEvent.CheckDrugInteraction -> checkDrugInteraction(event.drugA, event.drugB)
            is ToolsUiEvent.EvaluateLab -> evaluateLab(event.testName, event.value)
            is ToolsUiEvent.CalculatePaediatric -> calculatePaediatric(event.ageMonths, event.weightKg, event.heightCm, event.gender, event.drugName, event.indication)
            is ToolsUiEvent.CalculateBmi -> calculateBmi(event.weightKg, event.heightCm)
            is ToolsUiEvent.CalculateDueDate -> calculateDueDate(event.day, event.month, event.year)
            is ToolsUiEvent.ToggleReminder -> toggleReminder(event.id, event.enabled)
            is ToolsUiEvent.AddReminder -> addReminder(event.title, event.hour, event.minute)
            is ToolsUiEvent.DeleteReminder -> deleteReminder(event.id)
        }
    }

    fun selectTab(index: Int) { _selectedTab.value = index }
    fun setDrugQuery(query: String) { _drugSearchQuery.value = query }

    fun checkDrugInteraction(drugA: String, drugB: String) {
        if (drugA.isBlank() || drugB.isBlank()) return
        viewModelScope.launch { _checkedInteractions.value = drugRepository.checkInteraction(listOf(drugA.trim(), drugB.trim())) }
    }

    fun evaluateLab(testName: String, valueText: String) {
        viewModelScope.launch {
            val value = valueText.toDoubleOrNull()
            val test = healthToolsRepository.getLabTestByName(testName)
            _labEvaluation.value = when {
                value == null || !value.isFinite() -> LabEvaluationState(LabEvaluationKind.INVALID_INPUT)
                test == null -> LabEvaluationState(LabEvaluationKind.UNAVAILABLE)
                else -> {
                    val status = when {
                        value < test.normalLow -> LabComparisonStatus.BELOW_REFERENCE
                        value > test.normalHigh -> LabComparisonStatus.ABOVE_REFERENCE
                        else -> LabComparisonStatus.WITHIN_REFERENCE
                    }
                    LabEvaluationState(LabEvaluationKind.RESULT, value, test, status)
                }
            }
        }
    }

    fun calculatePaediatric(ageMonths: String, weightKg: String, heightCm: String, gender: String, drugName: String, indication: String) {
        calculatorJob?.cancel()
        calculatorJob = viewModelScope.launch {
            _calculatorState.value = CalculatorUiState(CalculatorKind.PAEDIATRIC, isRunning = true)
            when (val validation = ClinicalCalculatorInputValidator.paediatric(ageMonths, weightKg, heightCm, gender, drugName, indication)) {
                is CalculatorInputValidation.Invalid -> _calculatorState.value = CalculatorUiState(CalculatorKind.PAEDIATRIC, error = CalculatorErrorKind.VALIDATION)
                is CalculatorInputValidation.Valid -> {
                    val input = validation.value
                    val results = withContext(Dispatchers.Default) {
                        listOf(
                            ToolRegistry.executeTool("calculate_zscore", mapOf("age_months" to input.ageMonths, "weight_kg" to input.weightKg, "height_cm" to input.heightCm, "gender" to input.gender)),
                            ToolRegistry.executeTool("get_paediatric_dosing", mapOf("drug_name" to input.drugName, "weight_kg" to input.weightKg, "indication" to input.indication))
                        )
                    }
                    _calculatorState.value = combineToolResults(CalculatorKind.PAEDIATRIC, results)
                }
            }
        }
    }

    fun calculateBmi(weightKg: String, heightCm: String) {
        calculatorJob?.cancel()
        calculatorJob = viewModelScope.launch {
            _calculatorState.value = CalculatorUiState(CalculatorKind.BMI, isRunning = true)
            when (val validation = ClinicalCalculatorInputValidator.bmi(weightKg, heightCm)) {
                is CalculatorInputValidation.Invalid -> _calculatorState.value = CalculatorUiState(CalculatorKind.BMI, error = CalculatorErrorKind.VALIDATION)
                is CalculatorInputValidation.Valid -> {
                    val input = validation.value
                    val result = withContext(Dispatchers.Default) { ToolRegistry.executeTool("calculate_bmi", mapOf("weight_kg" to input.weightKg, "height_cm" to input.heightCm)) }
                    _calculatorState.value = combineToolResults(CalculatorKind.BMI, listOf(result))
                }
            }
        }
    }

    fun calculateDueDate(day: String, month: String, year: String) {
        calculatorJob?.cancel()
        calculatorJob = viewModelScope.launch {
            _calculatorState.value = CalculatorUiState(CalculatorKind.DUE_DATE, isRunning = true)
            when (val validation = ClinicalCalculatorInputValidator.dueDate(day, month, year)) {
                is CalculatorInputValidation.Invalid -> _calculatorState.value = CalculatorUiState(CalculatorKind.DUE_DATE, error = CalculatorErrorKind.VALIDATION)
                is CalculatorInputValidation.Valid -> {
                    val input = validation.value
                    val result = withContext(Dispatchers.Default) { ToolRegistry.executeTool("calculate_due_date", mapOf("day" to input.day, "month" to input.month, "year" to input.year)) }
                    _calculatorState.value = combineToolResults(CalculatorKind.DUE_DATE, listOf(result))
                }
            }
        }
    }

    private fun combineToolResults(kind: CalculatorKind, results: List<ToolResult>): CalculatorUiState {
        val failure = results.firstOrNull { !it.isSuccess }
        return if (failure != null) {
            CalculatorUiState(
                kind = kind,
                error = if (failure.status == ToolResultStatus.UNAVAILABLE) CalculatorErrorKind.UNAVAILABLE else CalculatorErrorKind.FAILED
            )
        } else {
            CalculatorUiState(kind = kind, result = results)
        }
    }

    fun toggleReminder(id: String, enabled: Boolean) { viewModelScope.launch { healthToolsRepository.toggleReminder(id, enabled) } }
    fun addReminder(title: String, hour: Int, minute: Int) {
        if (title.isBlank() || hour !in 0..23 || minute !in 0..59) return
        viewModelScope.launch { healthToolsRepository.saveReminder(Reminder(type = ReminderType.MEDICATION, title = title, timeHour = hour, timeMinute = minute, daysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7))) }
    }
    fun deleteReminder(id: String) { viewModelScope.launch { healthToolsRepository.deleteReminder(id) } }
}
