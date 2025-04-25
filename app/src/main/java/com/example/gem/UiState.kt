// app/src/main/java/com/example/gem/UiState.kt

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
    data class Loading(
        val attempt: Int = 0,
        val maxAttempts: Int = 5,
        val storyLength: Int = 0,
        val usedWordsCount: Int = 0,
        val totalWords: Int = 0,
        val missingWords: List<String> = emptyList()
    ) : UiState()

    /**
     * Text has been generated
     */
    data class Success(
        val englishVersion: String, // Stores version with separators for splitting
        val englishDisplayVersion: String, // Version without separators for display
        val russianVersion: String, // Stores version with separators for splitting
        val russianDisplayVersion: String = "", // Version without separators for display
        val selectedWords: List<String>,
        val isRussian: Boolean = false,
        val currentSpokenWord: String = "",
        val lastHighlightedSentence: String = "", // Last highlighted sentence
        val lastHighlightedSentenceIndex: Int = -1, // Added index of last highlighted sentence
        val generationTime: Double = 0.0,
        val isTranslating: Boolean = false,
        val isSpeaking: Boolean = false, // Flag for active text playback
        val lastSpokenWordIndex: Int = 0 // Index of last spoken word
    ) : UiState()

    /**
     * There was an error generating text
     */
    data class Error(val message: String) : UiState()
}