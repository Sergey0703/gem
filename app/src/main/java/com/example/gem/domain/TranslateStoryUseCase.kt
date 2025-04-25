// app/src/main/java/com/example/gem/domain/TranslateStoryUseCase.kt

package com.example.gem.domain

import android.util.Log
import com.example.gem.BuildConfig
import com.example.gem.UiState
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject

class TranslateStoryUseCase @Inject constructor() {
    private val TAG = "TranslateStoryUseCase"
    private var stateUpdateCallback: ((UiState) -> UiState) -> Unit = {}

    private val SENTENCE_SEPARATOR = "<<SENTENCE_END>>"

    // Error handler for coroutines
    private val errorHandler = CoroutineExceptionHandler { _, exception ->
        Log.e(TAG, "Coroutine exception: ${exception.message}", exception)
    }

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = BuildConfig.API_KEY
    )

    fun setStateUpdateCallback(callback: ((UiState) -> UiState) -> Unit) {
        stateUpdateCallback = callback
    }

    suspend fun toggleLanguage(currentState: UiState) {
        if (currentState is UiState.Success) {
            updateState { currentState.copy(isTranslating = true) }
            // If switching to Russian and no translation exists yet
            if (!currentState.isRussian && currentState.russianVersion.isEmpty()) {
                try {
                    val russianStory = translateStory(currentState.englishVersion)

                    // Create display version
                    val russianDisplayText = cleanTextForUI(russianStory)

                    Log.d(TAG, "Translation received")
                    Log.d(TAG, "Russian story length: ${russianStory.length}")

                    withContext(Dispatchers.Main) {
                        updateState { state ->
                            if (state is UiState.Success) {
                                state.copy(
                                    russianVersion = russianStory, // With separators
                                    russianDisplayVersion = russianDisplayText, // Without separators
                                    isRussian = true,
                                    isTranslating = false
                                )
                            } else state
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error translating story", e)
                    withContext(Dispatchers.Main) {
                        updateState { _ -> UiState.Error("Error translating story: ${e.localizedMessage}") }
                    }
                }
            } else {
                // Just toggle language if translation already exists
                delay(500) // Small delay for animation
                withContext(Dispatchers.Main) {
                    updateState { state ->
                        if (state is UiState.Success) {
                            state.copy(
                                isRussian = !state.isRussian,
                                isTranslating = false
                            )
                        } else state
                    }
                }
            }
        }
    }

    private suspend fun translateStory(englishText: String): String {
        val translationPrompt = """
            Translate this story to Russian. Keep the same formatting and paragraph breaks.
            Mark the translated equivalents of marked words with asterisks.
            
            CRITICAL INSTRUCTION: After each complete sentence (ending with period, exclamation mark, or question mark), insert the exact text "$SENTENCE_SEPARATOR". 
            DO NOT abbreviate or modify this separator in any way. 
            DO NOT skip adding this separator after ANY sentence.
            Include this separator even at paragraph breaks.
            
            Example format:
            This is the first sentence.$SENTENCE_SEPARATOR This is the second sentence.$SENTENCE_SEPARATOR
            
            Story to translate:
            $englishText
            
            Provide only the Russian translation, no additional text.
        """.trimIndent()

        Log.d(TAG, "Requesting translation")
        val response = generativeModel.generateContent(translationPrompt)
        var russianStory = response.text?.trim() ?: throw Exception("Empty translation response")

        Log.d(TAG, "Russian translation raw first 500 chars: ${russianStory.take(500)}")
        Log.d(TAG, "Russian contains separators: ${russianStory.contains(SENTENCE_SEPARATOR)}")
        Log.d(TAG, "Russian count of separators: ${russianStory.split(SENTENCE_SEPARATOR).size - 1}")

        // Clean text of asterisks but keep sentence separators
        russianStory = russianStory.replace(Regex("""\*([^*]+)\*""")) { matchResult ->
            matchResult.groupValues[1] // Return only text inside asterisks
        }

        return russianStory
    }

    private fun cleanTextForUI(text: String): String {
        return text.replace(SENTENCE_SEPARATOR, "")
    }

    private fun updateState(updater: (UiState) -> UiState) {
        stateUpdateCallback(updater)
    }
}