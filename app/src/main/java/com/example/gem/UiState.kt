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
        val englishVersion: String, // Хранит версию с разделителями для разбиения
        val englishDisplayVersion: String, // Версия без разделителей для отображения
        val russianVersion: String, // Хранит версию с разделителями для разбиения
        val russianDisplayVersion: String = "", // Версия без разделителей для отображения
        val selectedWords: List<String>,
        val isRussian: Boolean = false,
        val currentSpokenWord: String = "",
        val lastHighlightedSentence: String = "", // Последнее подсвеченное предложение
        val generationTime: Double = 0.0,
        val isTranslating: Boolean = false,
        val isSpeaking: Boolean = false, // Флаг активного воспроизведения текста
        val lastSpokenWordIndex: Int = 0 // Индекс последнего произнесенного слова
    ) : UiState()

    /**
     * There was an error generating text
     */
    data class Error(val message: String) : UiState()
}