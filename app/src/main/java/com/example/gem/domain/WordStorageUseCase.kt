// app/src/main/java/com/example/gem/domain/WordStorageUseCase.kt

package com.example.gem.domain

import android.util.Log
import com.example.gem.UiState
import com.example.gem.data.WordDao
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject

class WordStorageUseCase @Inject constructor(
    private val wordDao: WordDao
) {
    private val TAG = "WordStorageUseCase"
    private var stateUpdateCallback: ((UiState) -> UiState) -> Unit = {}

    // Number of words to use for each story
    private val STORY_WORDS_COUNT = 300
    // Maximum number of words to fetch from database
    private val MAX_WORDS_TO_FETCH = 300

    // Error handler for coroutines
    private val errorHandler = CoroutineExceptionHandler { _, exception ->
        Log.e(TAG, "Coroutine exception: ${exception.message}", exception)
    }

    fun setStateUpdateCallback(callback: ((UiState) -> UiState) -> Unit) {
        stateUpdateCallback = callback
    }

    suspend fun getWordsForStory(): List<String> {
        try {
            // Get words from database, sorted by last usage date (NULL first)
            val words = withContext(Dispatchers.IO + SupervisorJob() + errorHandler) {
                wordDao.getWordsToReview(Date(), MAX_WORDS_TO_FETCH).first()
            }

            if (words.isEmpty()) {
                throw Exception("Dictionary is empty. Please add some words first.")
            }

            // Convert Word list to list of English words
            val englishWords = words.map { it.english }

            // Shuffle the list of words for story variety
            val shuffledWords = englishWords.shuffled()

            // Select subset of words for the story
            val selectedWords = if (shuffledWords.size <= STORY_WORDS_COUNT) {
                shuffledWords
            } else {
                shuffledWords.subList(0, STORY_WORDS_COUNT)
            }

            return selectedWords
        } catch (e: Exception) {
            Log.e(TAG, "Error getting words for story", e)
            throw e
        }
    }

    suspend fun updateWordUsageDates(words: List<String>) {
        try {
            withContext(Dispatchers.IO + SupervisorJob() + errorHandler) {
                // Get all words from database
                val allWords = wordDao.getAllWords().first()
                // Create map of English word -> id
                val wordIdMap = allWords.associate { it.english.lowercase() to it.id }

                // For each word in the story, update its last usage date
                words.forEach { word ->
                    wordIdMap[word.lowercase()]?.let { wordId ->
                        wordDao.updateWordUsage(wordId, Date())
                        Log.d(TAG, "Updated usage date for word: $word (ID: $wordId)")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating word usage dates", e)
        }
    }

    private fun updateState(updater: (UiState) -> UiState) {
        stateUpdateCallback(updater)
    }
}