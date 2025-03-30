package com.example.gem

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gem.data.Word
import com.example.gem.data.WordDao
import com.opencsv.CSVReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class DictionaryViewModel @Inject constructor(
    private val wordDao: WordDao
) : ViewModel() {
    private val _uiState = MutableStateFlow<DictionaryUiState>(DictionaryUiState.Initial)
    val uiState: StateFlow<DictionaryUiState> = _uiState.asStateFlow()

    private var searchQuery = ""

    init {
        loadWords()
    }

    private fun loadWords() {
        viewModelScope.launch {
            try {
                wordDao.getAllWords().collect { words ->
                    val filteredWords = if (searchQuery.isBlank()) {
                        words
                    } else {
                        words.filter { word ->
                            word.english.contains(searchQuery, ignoreCase = true) ||
                            word.russian.contains(searchQuery, ignoreCase = true)
                        }
                    }
                    _uiState.value = DictionaryUiState.Success(filteredWords)
                }
            } catch (e: Exception) {
                _uiState.value = DictionaryUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun filterWords(query: String) {
        searchQuery = query
        loadWords()
    }

    fun importCsv(inputStream: InputStream) {
        viewModelScope.launch {
            try {
                _uiState.value = DictionaryUiState.Loading
                val reader = CSVReader(InputStreamReader(inputStream))
                val words = reader.readAll().drop(1) // Skip header row
                    .mapNotNull { row ->
                        if (row.size >= 4) {
                            Word(
                                english = row[0],
                                russian = row[1],
                                transcription = row[2],
                                example = row[3]
                            )
                        } else null
                    }
                wordDao.insertWords(words)
                loadWords() // Перезагружаем список слов
            } catch (e: Exception) {
                _uiState.value = DictionaryUiState.Error(e.message ?: "Error importing CSV")
            }
        }
    }

    fun addWord(word: Word) {
        viewModelScope.launch {
            try {
                wordDao.insertWord(word)
                loadWords() // Перезагружаем список слов
            } catch (e: Exception) {
                _uiState.value = DictionaryUiState.Error(e.message ?: "Error adding word")
            }
        }
    }

    fun updateWord(word: Word) {
        viewModelScope.launch {
            try {
                wordDao.updateWord(word)
                loadWords() // Перезагружаем список слов
            } catch (e: Exception) {
                _uiState.value = DictionaryUiState.Error(e.message ?: "Error updating word")
            }
        }
    }

    fun deleteWord(word: Word) {
        viewModelScope.launch {
            try {
                wordDao.deleteWord(word)
                loadWords() // Перезагружаем список слов
            } catch (e: Exception) {
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