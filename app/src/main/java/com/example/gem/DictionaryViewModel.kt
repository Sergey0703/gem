// app/src/main/java/com/example/gem/DictionaryViewModel.kt

package com.example.gem

import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gem.data.Word
import com.example.gem.data.WordDao
import com.opencsv.CSVParser
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReader
import com.opencsv.CSVReaderBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.InputStreamReader
import java.util.*
import javax.inject.Inject

private const val TAG = "DictionaryViewModel"

@HiltViewModel
class DictionaryViewModel @Inject constructor(
    private val wordDao: WordDao,
    private val textToSpeech: TextToSpeech
) : ViewModel() {
    private val _uiState = MutableStateFlow<DictionaryUiState>(DictionaryUiState.Initial)
    val uiState: StateFlow<DictionaryUiState> = _uiState.asStateFlow()

    private var searchQuery = ""
    private var currentSortOption = SortOption.DATE_ADDED
    private var isAscending = false
    private var cachedWords = listOf<Word>()

    init {
        loadWords()
        // Initialize Text-to-Speech
        textToSpeech.language = Locale.US
    }

    override fun onCleared() {
        super.onCleared()
        textToSpeech.shutdown()
    }

    fun playWord(word: Word) {
        // First try to play from URL if it exists in the example
        val soundUrl = extractSoundUrl(word.example)
        if (soundUrl != null) {
            playSoundFromUrl(soundUrl)
        } else {
            // If no URL, use Text-to-Speech
            textToSpeech.speak(word.english, TextToSpeech.QUEUE_FLUSH, null, null)
        }

        // Update the word's lastUsed date
        viewModelScope.launch {
            try {
                val updatedWord = word.copy(lastUsed = Date())
                wordDao.updateWord(updatedWord)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating word lastUsed date", e)
            }
        }
    }

    private fun extractSoundUrl(example: String): String? {
        // Extract sound URL from example (format: [sound:URL])
        val regex = Regex("\\[sound:([^\\]]+)\\]")
        return regex.find(example)?.groupValues?.get(1)
    }

    private fun playSoundFromUrl(url: String) {
        try {
            MediaPlayer().apply {
                setDataSource(url)
                prepareAsync()
                setOnPreparedListener { mp ->
                    mp.start()
                }
                setOnCompletionListener { mp ->
                    mp.release()
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    // If failed to play sound, use Text-to-Speech as fallback
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing sound from URL", e)
        }
    }

    private fun loadWords() {
        viewModelScope.launch {
            try {
                wordDao.getAllWords().collect { words ->
                    cachedWords = words
                    val filteredWords = filterWordsByQuery(words)
                    val sortedWords = sortWords(filteredWords)
                    _uiState.value = DictionaryUiState.Success(sortedWords)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading words", e)
                _uiState.value = DictionaryUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun filterWords(query: String) {
        searchQuery = query
        viewModelScope.launch {
            val filteredWords = filterWordsByQuery(cachedWords)
            val sortedWords = sortWords(filteredWords)
            _uiState.value = DictionaryUiState.Success(sortedWords)
        }
    }

    private fun filterWordsByQuery(words: List<Word>): List<Word> {
        return if (searchQuery.isBlank()) {
            words
        } else {
            words.filter { word ->
                word.english.contains(searchQuery, ignoreCase = true) ||
                        word.russian.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    fun sortWords(sortOption: SortOption, ascending: Boolean) {
        currentSortOption = sortOption
        isAscending = ascending

        viewModelScope.launch {
            val state = _uiState.value
            if (state is DictionaryUiState.Success) {
                val sortedWords = sortWords(state.words)
                _uiState.value = DictionaryUiState.Success(sortedWords)
            }
        }
    }

    private fun sortWords(words: List<Word>): List<Word> {
        return when (currentSortOption) {
            SortOption.NAME -> {
                if (isAscending) {
                    words.sortedBy { it.english.lowercase() }
                } else {
                    words.sortedByDescending { it.english.lowercase() }
                }
            }
            SortOption.LAST_USED -> {
                if (isAscending) {
                    // Null values first when ascending (oldest to newest)
                    words.sortedWith(compareBy(
                        { it.lastUsed == null }, // Nulls first
                        { it.lastUsed } // Then by date
                    ))
                } else {
                    // Null values last when descending (newest to oldest)
                    words.sortedWith(compareByDescending<Word> {
                        it.lastUsed
                    }.thenBy {
                        it.lastUsed == null // Nulls last
                    })
                }
            }
            SortOption.DATE_ADDED -> {
                if (isAscending) {
                    words.sortedBy { it.dateAdded }
                } else {
                    words.sortedByDescending { it.dateAdded }
                }
            }
        }
    }

    private fun cleanString(input: String): String {
        return input
            .replace("\"", "") // Remove double quotes
            .replace("<br>", "") // Remove <br> tag
            .replace("<b>", "") // Remove <b> tag
            .replace("</b>", "") // Remove </b> tag
            .replace(Regex("<img src='[^']*'>"), "") // Remove img tags
            .replace(Regex("\\[sound:[^\\]]*\\]"), "") // Remove sound tags
            .replace(Regex("\\{\\{c1::[^}]*\\}\\}"), "") // Remove c1 tags
            .trim() // Remove spaces at beginning and end
    }

    fun importCsv(inputStream: InputStream) {
        viewModelScope.launch {
            try {
                _uiState.value = DictionaryUiState.Loading
                Log.d(TAG, "Starting CSV import")

                val parser = CSVParserBuilder()
                    .withSeparator(';')
                    .build()
                val reader = CSVReaderBuilder(InputStreamReader(inputStream))
                    .withCSVParser(parser)
                    .build()

                val allRows = reader.readAll()
                Log.d(TAG, "Read ${allRows.size} rows from CSV")

                if (allRows.isEmpty()) {
                    Log.w(TAG, "CSV file is empty")
                    _uiState.value = DictionaryUiState.Error("File is empty")
                    return@launch
                }

                val words = allRows.mapNotNull { row: Array<String> ->
                    try {
                        if (row.size < 2) {
                            Log.w(TAG, "Invalid row: ${row.joinToString()}")
                            return@mapNotNull null
                        }

                        val english = cleanString(row[0])
                        val russian = cleanString(row[1])
                        val transcription = if (row.size >= 4) cleanString(row[3]) else ""
                        val example = if (row.size >= 5) cleanString(row[4]) else ""

                        Log.d(TAG, "Processing row: $english - $russian - $transcription - $example")
                        Word(
                            english = english,
                            russian = russian,
                            transcription = transcription,
                            example = example,
                            dateAdded = Date()
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing row: ${row.joinToString()}", e)
                        null
                    }
                }

                Log.d(TAG, "Successfully processed ${words.size} words")
                wordDao.insertWords(words)
                loadWords() // Reload words list instead of directly setting state
            } catch (e: Exception) {
                Log.e(TAG, "Error importing CSV", e)
                _uiState.value = DictionaryUiState.Error("Import error: ${e.message}")
            }
        }
    }

    fun addWord(word: Word) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Adding new word: ${word.english}")
                // Ensure the word has a dateAdded value
                val wordToAdd = if (word.dateAdded.time == 0L) {
                    word.copy(dateAdded = Date())
                } else {
                    word
                }
                wordDao.insertWord(wordToAdd)
                loadWords() // Reload words list
            } catch (e: Exception) {
                Log.e(TAG, "Error adding word", e)
                _uiState.value = DictionaryUiState.Error(e.message ?: "Error adding word")
            }
        }
    }

    fun updateWord(word: Word) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Updating word: ${word.english}")
                wordDao.updateWord(word)
                loadWords() // Reload words list
            } catch (e: Exception) {
                Log.e(TAG, "Error updating word", e)
                _uiState.value = DictionaryUiState.Error(e.message ?: "Error updating word")
            }
        }
    }

    fun deleteWord(word: Word) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Deleting word: ${word.english}")
                wordDao.deleteWord(word)
                loadWords() // Reload words list
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting word", e)
                _uiState.value = DictionaryUiState.Error(e.message ?: "Error deleting word")
            }
        }
    }
}

sealed class DictionaryUiState {
    object Initial : DictionaryUiState()
    object Loading : DictionaryUiState()
    data class Success(val words: List<Word>) : DictionaryUiState()
    data class Error(val message: String) : DictionaryUiState()
}