package com.example.gem

/**
 * A sealed hierarchy describing the state of the text generation.
 */
sealed interface UiState {

    /**
     * Empty state when the screen is first shown
     */
    object Initial : UiState

    /**
     * Still loading
     */
    object Loading : UiState

    /**
     * Text has been generated
     */
    data class Success(
        val outputText: String,
        val selectedWords: List<String>,
        val isRussian: Boolean,
        val translations: String = "",
        val englishVersion: String = "",
        val russianVersion: String = ""
    ) : UiState

    /**
     * There was an error generating text
     */
    data class Error(val errorMessage: String) : UiState
}