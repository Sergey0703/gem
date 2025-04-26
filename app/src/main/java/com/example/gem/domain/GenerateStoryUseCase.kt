// app/src/main/java/com/example/gem/domain/GenerateStoryUseCase.kt

package com.example.gem.domain

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.example.gem.BuildConfig
import com.example.gem.UiState
import com.google.ai.client.generativeai.GenerativeModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject

class GenerateStoryUseCase @Inject constructor(
    private val wordStorageUseCase: WordStorageUseCase,
    @ApplicationContext private val context: Context
) {
    private val TAG = "GenerateStoryUseCase"
    private var stateUpdateCallback: ((UiState) -> UiState) -> Unit = {}

    // Special sentence separator constant
    private val SENTENCE_SEPARATOR = "<<SENTENCE_END>>"

    // Error handler for coroutines
    private val errorHandler = CoroutineExceptionHandler { _, exception ->
        Log.e(TAG, "Coroutine exception: ${exception.message}", exception)
    }

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = BuildConfig.API_KEY.also { key ->
            Log.d(TAG, "API Key length: ${key.length}")
            if (key.isEmpty()) {
                Log.e(TAG, "API Key is empty!")
            }
        }
    )

    fun setStateUpdateCallback(callback: ((UiState) -> UiState) -> Unit) {
        stateUpdateCallback = callback
    }

    suspend fun generateStory(prompt: String, maxAttempts: Int = 5): Triple<String, String, List<String>> {
        try {
            // Get words from dictionary
            val availableWords = wordStorageUseCase.getWordsForStory()

            updateState { currentState ->
                UiState.Loading(
                    maxAttempts = maxAttempts,
                    totalWords = availableWords.size
                )
            }

            Log.d(TAG, "Starting story generation with words: ${availableWords.joinToString(", ")}")

            var attempt = 1
            var lastMissingWords = availableWords
            var englishStory = ""
            val errorThreshold = availableWords.size / 10 // 10% of total word count

            while (attempt <= maxAttempts) {
                updateState { _ ->
                    UiState.Loading(
                        attempt = attempt,
                        maxAttempts = maxAttempts,
                        totalWords = availableWords.size
                    )
                }

                val storyPrompt = if (attempt == 1) {
                    """
                        You are a creative storyteller. Create an engaging and coherent story that MUST use ALL of these words: ${availableWords.joinToString(", ")}.
                        
                        Rules:
                        1. Use EVERY word from the list exactly once - this is the most important rule
                        2. Mark each used word with asterisks like this: *word*
                        3. Create a story that flows naturally despite using all required words
                        4. Break the story into logical paragraphs for readability
                        5. Make sure the story has a clear beginning, middle, and end
                        6. Focus on making connections between words to create a coherent narrative
                        7. EXTREMELY IMPORTANT: After each complete sentence (ending with period, exclamation mark, or question mark), insert the exact text "$SENTENCE_SEPARATOR". 
                           DO NOT abbreviate or modify this separator in any way. 
                           DO NOT skip adding this separator after ANY sentence.
                           Include this separator even at paragraph breaks.
                        
                        Example format:
                        This is the first sentence.$SENTENCE_SEPARATOR This is the second sentence.$SENTENCE_SEPARATOR
                        
                        Important:
                        - Every word from the list MUST be used exactly once
                        - Only mark the exact words with asterisks
                        - Add "$SENTENCE_SEPARATOR" after EVERY sentence
                        - Write only the story, no additional text or explanations
                        - Make sure the story makes sense and reads naturally
                    """.trimIndent()
                } else {
                    """
                        Please revise the story to include these missing words: ${lastMissingWords.joinToString(", ")}.
                        
                        Previous attempt: 
                        $englishStory
                        
                        Rules:
                        1. Use EVERY missing word exactly once while keeping the story coherent
                        2. Mark the used words with asterisks like this: *word*
                        3. You can modify the existing story to naturally incorporate the missing words
                        4. Keep the story's structure and main ideas, but expand it to include all words
                        5. Ensure the story flows naturally and makes sense
                        6. EXTREMELY IMPORTANT: After each complete sentence (ending with period, exclamation mark, or question mark), insert the exact text "$SENTENCE_SEPARATOR". 
                           DO NOT abbreviate or modify this separator in any way. 
                           DO NOT skip adding this separator after ANY sentence.
                           Include this separator even at paragraph breaks.
                        
                        Example format:
                        This is the first sentence.$SENTENCE_SEPARATOR This is the second sentence.$SENTENCE_SEPARATOR
                        
                        Important:
                        - Every word from the missing list MUST be used
                        - Only mark the exact words with asterisks
                        - Add "$SENTENCE_SEPARATOR" after EVERY sentence
                        - Write the complete story, not just the additions
                    """.trimIndent()
                }

                Log.d(TAG, "Generating story - Attempt $attempt of $maxAttempts")
                Log.d(TAG, "Current API key length: ${BuildConfig.API_KEY.length}, first 5 chars: ${BuildConfig.API_KEY.take(5)}...")

                try {
                    // Логируем параметры запроса
                    Log.d(TAG, "Request prompt size: ${storyPrompt.length} characters")
                    Log.d(TAG, "Words count to use: ${availableWords.size}")
                    Log.d(TAG, "Network connection available: ${isNetworkAvailable(context)}")

                    val response = withContext(Dispatchers.IO + SupervisorJob() + errorHandler) {
                        try {
                            // Засекаем время выполнения запроса
                            val startTime = System.currentTimeMillis()
                            val apiResponse = generativeModel.generateContent(storyPrompt)
                            val endTime = System.currentTimeMillis()
                            Log.d(TAG, "API request completed in ${endTime - startTime}ms")

                            apiResponse
                        } catch (e: Exception) {
                            // Детальное логирование ошибки API
                            Log.e(TAG, "API call exception: ${e.javaClass.name}", e)
                            Log.e(TAG, "Error message: ${e.message}")
                            Log.e(TAG, "Cause: ${e.cause?.message}")

                            // Проверка на специфические ошибки Google API
                            when {
                                e.message?.contains("403") == true ->
                                    Log.e(TAG, "Permission denied (403) - API key may be invalid or restricted")
                                e.message?.contains("429") == true ->
                                    Log.e(TAG, "Too many requests (429) - Rate limit or quota exceeded")
                                e.message?.contains("401") == true ->
                                    Log.e(TAG, "Unauthorized (401) - Authentication failed")
                                e.message?.contains("timeout") == true || e.message?.contains("timed out") == true ->
                                    Log.e(TAG, "Request timed out - Check network or reduce prompt size")
                                e.message?.contains("UNAVAILABLE") == true ->
                                    Log.e(TAG, "Service unavailable - Gemini API may be down or unreachable")
                                e.message?.contains("INVALID_ARGUMENT") == true ->
                                    Log.e(TAG, "Invalid argument - Check prompt format and content")
                                e.message?.contains("RESOURCE_EXHAUSTED") == true ->
                                    Log.e(TAG, "Resource exhausted - Quota exceeded")
                                else ->
                                    Log.e(TAG, "Unspecified API error")
                            }

                            throw e
                        }
                    }

                    // Проверка ответа
                    if (response.text == null) {
                        Log.e(TAG, "Received null text in response (response object: ${response.javaClass.name})")
                        throw Exception("Empty response from API (null text)")
                    }

                    englishStory = response.text?.trim() ?: ""

                    // Проверка на пустой ответ
                    if (englishStory.isEmpty()) {
                        Log.e(TAG, "Received empty text in response")
                        throw Exception("Empty response from API (empty text)")
                    }

                    // Детальное логирование успешного ответа
                    Log.d(TAG, "Response received successfully, length: ${englishStory.length} chars")
                    Log.d(TAG, "Response first 100 chars: ${englishStory.take(100)}")
                    Log.d(TAG, "Response last 100 chars: ${englishStory.takeLast(100)}")
                    Log.d(TAG, "Contains separators: ${englishStory.contains(SENTENCE_SEPARATOR)}")
                    Log.d(TAG, "Count of separators: ${englishStory.split(SENTENCE_SEPARATOR).size - 1}")

                    // Проверка на потенциальные проблемы в ответе
                    if (!englishStory.contains(SENTENCE_SEPARATOR)) {
                        Log.w(TAG, "WARNING: Response doesn't contain expected sentence separators!")
                    }

                    if (!englishStory.contains("*")) {
                        Log.w(TAG, "WARNING: Response doesn't contain any asterisks for marking words!")
                    }

                    // Check for used words
                    val usedWords = availableWords.filter { word ->
                        englishStory.contains("*$word*", ignoreCase = true)
                    }

                    // Words not yet used, prepare for next attempt
                    lastMissingWords = availableWords.filter { word ->
                        !englishStory.contains("*$word*", ignoreCase = true)
                    }

                    // Update state with progress
                    updateState { _ ->
                        UiState.Loading(
                            attempt = attempt,
                            maxAttempts = maxAttempts,
                            usedWordsCount = usedWords.size,
                            totalWords = availableWords.size,
                            storyLength = englishStory.length,
                            missingWords = lastMissingWords
                        )
                    }

                    Log.d(TAG, "Story generated - Attempt $attempt")
                    Log.d(TAG, "English story length: ${englishStory.length}")
                    Log.d(TAG, "Used words count: ${usedWords.size}/${availableWords.size}")
                    Log.d(TAG, "Missing words: ${lastMissingWords.joinToString(", ")}")

                    if (lastMissingWords.isEmpty()) {
                        // All words used, update state and finish
                        updateState { _ ->
                            UiState.Success(
                                englishVersion = englishStory,
                                englishDisplayVersion = cleanTextForUI(cleanTextForDisplay(englishStory)),
                                russianVersion = "",
                                russianDisplayVersion = "",
                                selectedWords = availableWords,
                                isRussian = false,
                                currentSpokenWord = "",
                                lastHighlightedSentence = "",
                                generationTime = 0.0,
                                isSpeaking = false,
                                lastSpokenWordIndex = 0
                            )
                        }
                        Log.d(TAG, "Story generation completed successfully")

                        // Update word usage dates
                        wordStorageUseCase.updateWordUsageDates(availableWords)

                        return Triple(englishStory, cleanTextForUI(cleanTextForDisplay(englishStory)), availableWords)
                    } else {
                        Log.w(TAG, "Missing ${lastMissingWords.size} words in attempt $attempt: ${lastMissingWords.joinToString(", ")}")

                        if (attempt == maxAttempts && lastMissingWords.size >= errorThreshold) {
                            throw Exception("Failed to use enough words after $maxAttempts attempts. Missing ${lastMissingWords.size} words (threshold: $errorThreshold).")
                        } else if (attempt == maxAttempts) {
                            // If this is the last attempt but fewer words are missing than the threshold, use current version
                            updateState { _ ->
                                UiState.Success(
                                    englishVersion = englishStory,
                                    englishDisplayVersion = cleanTextForUI(cleanTextForDisplay(englishStory)),
                                    russianVersion = "",
                                    russianDisplayVersion = "",
                                    selectedWords = availableWords,
                                    isRussian = false,
                                    currentSpokenWord = "",
                                    lastHighlightedSentence = "",
                                    generationTime = 0.0,
                                    isSpeaking = false,
                                    lastSpokenWordIndex = 0
                                )
                            }
                            Log.d(TAG, "Story generation completed with ${lastMissingWords.size} missing words (below threshold)")

                            // Update usage dates only for used words
                            val usedWordsSet = usedWords.toSet()
                            wordStorageUseCase.updateWordUsageDates(usedWordsSet.toList())

                            return Triple(englishStory, cleanTextForUI(cleanTextForDisplay(englishStory)), availableWords)
                        }
                    }

                } catch (e: Exception) {
                    // Детальное логирование всех ошибок запроса
                    Log.e(TAG, "Error in attempt $attempt: ${e.javaClass.name}: ${e.message}", e)

                    // Если это последняя попытка, добавляем больше деталей
                    if (attempt == maxAttempts) {
                        Log.e(TAG, "Final attempt ($maxAttempts) failed. Request details:")
                        Log.e(TAG, "- Prompt first 200 chars: ${storyPrompt.take(200)}...")
                        Log.e(TAG, "- Available words: ${availableWords.joinToString(", ")}")
                        Log.e(TAG, "- Current device time: ${Date()}")
                        e.stackTrace.take(5).forEach { element ->
                            Log.e(TAG, "Stack: $element")
                        }
                        throw e
                    }

                    // Если не последняя попытка, ждем с логированием
                    Log.w(TAG, "Attempt $attempt failed. Will retry in 2 seconds...")
                    delay(2000) // Небольшая пауза перед следующей попыткой
                }

                attempt++
            }
        } catch (e: Exception) {
            // Полная информация об ошибке включая стек вызовов
            Log.e(TAG, "Error generating story: ${e.javaClass.name}: ${e.message}", e)

            // Детализируем ошибки API для Google Gemini
            when {
                e.message?.contains("RESOURCE_EXHAUSTED") == true -> {
                    Log.e(TAG, "API quota exceeded. You've reached your limit of requests.", e)
                    updateState { _ -> UiState.Error("API quota exceeded. You've reached your request limit.") }
                }
                e.message?.contains("INVALID_ARGUMENT") == true -> {
                    Log.e(TAG, "Invalid request parameters sent to Gemini API", e)
                    updateState { _ -> UiState.Error("Invalid parameters in API request: ${e.message}") }
                }
                e.message?.contains("PERMISSION_DENIED") == true -> {
                    Log.e(TAG, "API key is invalid or does not have required permissions", e)
                    updateState { _ -> UiState.Error("API key error: Invalid or insufficient permissions") }
                }
                e.message?.contains("UNAUTHENTICATED") == true -> {
                    Log.e(TAG, "Authentication failed - API key may be invalid", e)
                    updateState { _ -> UiState.Error("API key authentication failed. Please check your API key.") }
                }
                e.message?.contains("UNAVAILABLE") == true -> {
                    Log.e(TAG, "Gemini API service is currently unavailable", e)
                    updateState { _ -> UiState.Error("Gemini API service is currently unavailable. Please try again later.") }
                }
                e.message?.contains("timeout") == true || e.message?.contains("timed out") == true -> {
                    Log.e(TAG, "Request timed out - network issues or large prompt", e)
                    updateState { _ -> UiState.Error("Request timed out. Check your internet connection and try again.") }
                }
                else -> {
                    // Общее сообщение об ошибке с максимумом деталей
                    Log.e(TAG, "Unhandled error generating story", e)
                    updateState { _ -> UiState.Error("Error generating story: ${e.localizedMessage}") }
                }
            }
            return Triple("", "", listOf())
        }
        return Triple("", "", listOf())
    }

    // Remove only asterisks but KEEP sentence separators
    fun cleanTextForDisplay(text: String): String {
        return text
            .replace(Regex("""\*([^*]+)\*""")) { matchResult ->
                matchResult.groupValues[1] // Return only text within asterisks
            }
    }

    // Remove separators only for UI display
    fun cleanTextForUI(text: String): String {
        return text.replace(SENTENCE_SEPARATOR, "")
    }

    fun splitIntoSentences(text: String): List<String> {
        Log.d(TAG, "Original text contains ${text.count { it == '.' }} periods")
        Log.d(TAG, "Original text contains ${text.count { it == '!' }} exclamation marks")
        Log.d(TAG, "Original text contains ${text.count { it == '?' }} question marks")
        Log.d(TAG, "Original text contains ${text.split(SENTENCE_SEPARATOR).size - 1} separators")

        val sentences = text.split(SENTENCE_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it + SENTENCE_SEPARATOR } // Add separator back for correct comparison

        Log.d(TAG, "Split text into ${sentences.size} sentences using separators")
        sentences.forEachIndexed { index, sentence ->
            if (index < 3) { // Log only first few sentences to save space
                Log.d(TAG, "Sentence ${index + 1}: first 50 chars: ${sentence.take(50)}...")
            }
        }

        // If separators didn't work (only 1 sentence), log detailed info for debugging
        if (sentences.size <= 1 && text.length > 100) {
            Log.w(TAG, "Failed to split by separators, text too long for single sentence")
            // For logging only, don't return this result
            val fallbackSentences = text.split(Regex("(?<=[.!?])\\s+(?=[A-ZА-Я])"))
            Log.d(TAG, "Fallback splitting would yield ${fallbackSentences.size} sentences")

            // Log first and last 100 characters for analysis
            val start = text.take(100)
            val end = if (text.length > 100) text.takeLast(100) else ""
            Log.d(TAG, "Text start: $start")
            Log.d(TAG, "Text end: $end")
        }

        return sentences
    }

    // Вспомогательная функция для проверки сетевого подключения
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val networkCapabilities = connectivityManager.activeNetwork ?: return false
            val actNw = connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
            return actNw.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            return activeNetworkInfo != null && activeNetworkInfo.isConnected
        }
    }

    private fun updateState(updater: (UiState) -> UiState) {
        stateUpdateCallback(updater)
    }
}