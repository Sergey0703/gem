// app/src/main/java/com/example/gem/domain/WordInfoUseCase.kt

package com.example.gem.domain

import android.util.Log
import com.example.gem.BuildConfig
import com.example.gem.UiState
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class WordInfoUseCase @Inject constructor() {
    private val TAG = "WordInfoUseCase"
    private var stateUpdateCallback: ((UiState) -> UiState) -> Unit = {}

    // Error handler for coroutines
    private val errorHandler = CoroutineExceptionHandler { _, exception ->
        Log.e(TAG, "Coroutine exception: ${exception.message}", exception)
    }

    // Coroutine scope for this use case
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob() + errorHandler)

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = BuildConfig.API_KEY
    )

    fun setStateUpdateCallback(callback: ((UiState) -> UiState) -> Unit) {
        stateUpdateCallback = callback
    }

    suspend fun getWordInfo(word: String): Triple<String, String, String> {
        val prompt = """
            For the English word "$word", provide:
            1. The International Phonetic Alphabet (IPA) transcription in square brackets
            2. The Russian translation
            3. A simple example sentence in English
            
            Format your response exactly like this:
            TRANSCRIPTION: [phonetic symbols here]
            TRANSLATION: Russian translation here
            EXAMPLE: Example sentence here
            
            For example, if the word is "cat":
            TRANSCRIPTION: [kæt]
            TRANSLATION: кошка
            EXAMPLE: I have a black cat.
            
            Make sure the transcription uses proper IPA symbols and is enclosed in square brackets.
        """.trimIndent()

        return try {
            val response = withContext(Dispatchers.IO + SupervisorJob() + errorHandler) {
                generativeModel.generateContent(prompt).text?.trim() ?: ""
            }

            // Parse the response
            val transcription = response.substringAfter("TRANSCRIPTION: ")
                .substringBefore("\n")
                .trim()

            val translation = response.substringAfter("TRANSLATION: ")
                .substringBefore("\n")
                .trim()

            val example = response.substringAfter("EXAMPLE: ")
                .trim()

            Triple(transcription, translation, example)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting word info: ${e.message}", e)
            Triple("[${word}]", "Ошибка перевода", "Error getting example")
        }
    }

    fun getWordInfoAndUpdate(word: String, onResult: (Triple<String, String, String>) -> Unit) {
        scope.launch(Dispatchers.IO + SupervisorJob() + errorHandler) {
            try {
                val result = getWordInfo(word)
                withContext(Dispatchers.Main) {
                    onResult(result)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in getWordInfoAndUpdate: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onResult(Triple("[${word}]", "Ошибка перевода", "Error getting example"))
                }
            }
        }
    }

    private fun updateState(updater: (UiState) -> UiState) {
        stateUpdateCallback(updater)
    }
}