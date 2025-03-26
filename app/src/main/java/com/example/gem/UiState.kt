package com.example.gem

/**
 * A sealed hierarchy describing the state of the text generation.
 */
sealed class UiState {

    /**
     * Empty state when the screen is first shown
     */
    object Initial : UiState()

    /**
     * Still loading
     */
    object Loading : UiState()

    /**
     * Text has been generated
     */
    data class Success(
        val outputText: String = "",
        val selectedWords: List<String> = emptyList(),
        val isRussian: Boolean = false,
        val translations: String = "",
        val englishVersion: String = "",
        val russianVersion: String = "",
        val currentSpokenWord: String = ""
    ) : UiState()

    /**
     * There was an error generating text
     */
    data class Error(val message: String) : UiState()
}