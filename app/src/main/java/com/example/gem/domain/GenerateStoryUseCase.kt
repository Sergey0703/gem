// app/src/main/java/com/example/gem/domain/GenerateStoryUseCase.kt

package com.example.gem.domain

import android.content.Context
import android.util.Log
import com.example.gem.BuildConfig
import com.example.gem.UiState
import com.google.ai.client.generativeai.GenerativeModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
                val response = withContext(Dispatchers.IO + SupervisorJob() + errorHandler) {
                    generativeModel.generateContent(storyPrompt)
                }
                englishStory = response.text?.trim() ?: throw Exception("Empty response from API")

                Log.d(TAG, "Raw API response first 500 chars: ${englishStory.take(500)}")
                Log.d(TAG, "Contains separators: ${englishStory.contains(SENTENCE_SEPARATOR)}")
                Log.d(TAG, "Count of separators: ${englishStory.split(SENTENCE_SEPARATOR).size - 1}")

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

                attempt++
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating story", e)
            updateState { _ -> UiState.Error("Error generating story: ${e.localizedMessage}") }
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

    private fun updateState(updater: (UiState) -> UiState) {
        stateUpdateCallback(updater)
    }
}