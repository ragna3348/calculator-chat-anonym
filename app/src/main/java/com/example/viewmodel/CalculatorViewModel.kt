package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CalculationHistoryEntity
import com.example.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DecimalFormat

data class CalculatorUiState(
    val displayValue: String = "0",
    val formulaString: String = "",
    val previousResult: String? = null,
    val isNewNumber: Boolean = true,
    val isSecretUnlocked: Boolean = false,
    val showHistoryDialog: Boolean = false,
    val matchedContact: com.example.data.model.ChatEntity? = null,
    val currentMathFormulaCandidate: String? = null,
    val showAddContactDialog: Boolean = false,
    val pendingAddFormula: String = ""
)

sealed class CalculatorEvent {
    data object OpenSecretVault : CalculatorEvent()
    data class OpenChatWithContact(
        val chatId: String,
        val contactName: String,
        val mathUsername: String = ""
    ) : CalculatorEvent()
    data class ShowToast(val message: String) : CalculatorEvent()
}

class CalculatorViewModel(
    private val repository: AppRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CalculatorEvent>()
    val events: SharedFlow<CalculatorEvent> = _events.asSharedFlow()

    val calculationHistory: StateFlow<List<CalculationHistoryEntity>> = repository.calcHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var firstOperand: Double? = null
    private var pendingOperator: String? = null
    private var secretPattern = ""

    private val decimalFormat = DecimalFormat("#,###.########")

    fun onDigit(digit: String) {
        val current = _uiState.value.displayValue
        val isNew = _uiState.value.isNewNumber

        val newDisplay = if (isNew || current == "0") {
            digit
        } else {
            if (current.length < 12) current + digit else current
        }

        secretPattern += digit

        _uiState.value = _uiState.value.copy(
            displayValue = newDisplay,
            isNewNumber = false
        )

        checkCurrentExpressionForContacts()
    }

    fun onDecimal() {
        val current = _uiState.value.displayValue
        if (!current.contains(".")) {
            _uiState.value = _uiState.value.copy(
                displayValue = "$current.",
                isNewNumber = false
            )
            secretPattern += "."
        }
    }

    fun onOperator(op: String) {
        val current = _uiState.value.displayValue.toDoubleOrNull() ?: 0.0
        secretPattern += op

        if (firstOperand == null) {
            firstOperand = current
        } else if (pendingOperator != null) {
            val result = calculate(firstOperand!!, current, pendingOperator!!)
            firstOperand = result
            _uiState.value = _uiState.value.copy(
                displayValue = formatNumber(result)
            )
        }

        pendingOperator = op
        _uiState.value = _uiState.value.copy(
            formulaString = "${formatNumber(firstOperand!!)} $op",
            isNewNumber = true
        )

        checkCurrentExpressionForContacts()
    }

    private fun getCurrentFullFormula(): String {
        val formula = _uiState.value.formulaString.trim()
        val display = _uiState.value.displayValue.trim()
        val raw = if (formula.isNotEmpty()) "$formula $display" else display
        return com.example.utils.MathUsernameGenerator.normalizeFormula(raw)
    }

    private fun checkCurrentExpressionForContacts() {
        val full = getCurrentFullFormula()
        if (com.example.utils.MathUsernameGenerator.isMathExpression(full) && full != "99+99") {
            viewModelScope.launch {
                val contact = repository.findChatByMathUsername(full)
                _uiState.value = _uiState.value.copy(
                    matchedContact = contact,
                    currentMathFormulaCandidate = full
                )
            }
        } else {
            _uiState.value = _uiState.value.copy(
                matchedContact = null,
                currentMathFormulaCandidate = null
            )
        }
    }

    fun onEquals() {
        val formula = _uiState.value.formulaString
        val display = _uiState.value.displayValue

        // SECRET MASTER TRIGGER: Check for 99+99
        val normalizedFormula = (formula + display).replace(" ", "").replace("×", "*").replace("÷", "/")
        if (normalizedFormula == "99+99" || secretPattern.endsWith("99+99") || (firstOperand == 99.0 && pendingOperator == "+" && display == "99")) {
            viewModelScope.launch {
                _events.emit(CalculatorEvent.OpenSecretVault)
            }
            return
        }

        val fullNormalized = getCurrentFullFormula()

        // MATH USERNAME CONTACT TRIGGER
        if (com.example.utils.MathUsernameGenerator.isMathExpression(fullNormalized)) {
            viewModelScope.launch {
                val contact = repository.findChatByMathUsername(fullNormalized)
                if (contact != null) {
                    _events.emit(CalculatorEvent.OpenChatWithContact(contact.id, contact.contactName))
                    return@launch
                } else {
                    // It's a valid math username formula, but not in existing contacts -> prompt to add
                    _uiState.value = _uiState.value.copy(
                        showAddContactDialog = true,
                        pendingAddFormula = fullNormalized
                    )
                }
            }
        }

        if (firstOperand != null && pendingOperator != null) {
            val second = display.toDoubleOrNull() ?: 0.0
            val fullExpression = "$formula $display"
            val result = calculate(firstOperand!!, second, pendingOperator!!)
            val formattedResult = formatNumber(result)

            _uiState.value = _uiState.value.copy(
                displayValue = formattedResult,
                formulaString = "$fullExpression =",
                previousResult = formattedResult,
                isNewNumber = true,
                matchedContact = null,
                currentMathFormulaCandidate = null
            )

            // Save to DB
            viewModelScope.launch {
                repository.saveCalculation(fullExpression, formattedResult)
            }

            firstOperand = null
            pendingOperator = null
            secretPattern = ""
        }
    }

    fun onStartChatWithMatchedContact() {
        val contact = _uiState.value.matchedContact ?: return
        viewModelScope.launch {
            _events.emit(CalculatorEvent.OpenChatWithContact(contact.id, contact.contactName, contact.mathUsername))
        }
    }

    fun onConfirmAddContact(name: String) {
        val formula = _uiState.value.pendingAddFormula
        if (formula.isBlank()) return
        viewModelScope.launch {
            val newChat = repository.createOrGetChatWithMathUser(formula, name)
            _uiState.value = _uiState.value.copy(showAddContactDialog = false, pendingAddFormula = "")
            _events.emit(CalculatorEvent.OpenChatWithContact(newChat.id, newChat.contactName, newChat.mathUsername))
        }
    }

    fun dismissAddContactDialog() {
        _uiState.value = _uiState.value.copy(showAddContactDialog = false, pendingAddFormula = "")
    }

    fun onClear() {
        firstOperand = null
        pendingOperator = null
        secretPattern = ""
        _uiState.value = CalculatorUiState()
    }

    fun onDelete() {
        val current = _uiState.value.displayValue
        if (current.length > 1) {
            _uiState.value = _uiState.value.copy(
                displayValue = current.dropLast(1)
            )
        } else {
            _uiState.value = _uiState.value.copy(displayValue = "0", isNewNumber = true)
        }
        checkCurrentExpressionForContacts()
    }

    fun onPercent() {
        val current = _uiState.value.displayValue.toDoubleOrNull() ?: 0.0
        val result = current / 100.0
        _uiState.value = _uiState.value.copy(
            displayValue = formatNumber(result),
            isNewNumber = true
        )
    }

    fun onToggleSign() {
        val current = _uiState.value.displayValue.toDoubleOrNull() ?: 0.0
        val result = -current
        _uiState.value = _uiState.value.copy(
            displayValue = formatNumber(result)
        )
    }

    fun triggerSecretDirect() {
        viewModelScope.launch {
            _events.emit(CalculatorEvent.OpenSecretVault)
        }
    }

    fun toggleHistoryDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showHistoryDialog = show)
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearCalculationHistory()
        }
    }

    private fun calculate(a: Double, b: Double, op: String): Double {
        return when (op) {
            "+" -> a + b
            "-", "−" -> a - b
            "×", "*" -> a * b
            "÷", "/" -> if (b != 0.0) a / b else 0.0
            else -> b
        }
    }

    private fun formatNumber(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            decimalFormat.format(value)
        }
    }
}
