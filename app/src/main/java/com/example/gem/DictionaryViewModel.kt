package com.example.gem

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gem.data.Word
import com.example.gem.data.WordDatabase
import com.opencsv.CSVReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStreamReader

class DictionaryViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<DictionaryUiState>(DictionaryUiState.Initial)
    val uiState: StateFlow<DictionaryUiState> = _uiState.asStateFlow()

    private fun removeQuotes(value: String): String {
        return value.trim().removeSurrounding("\"")
    }

    fun importCsv(context: Context, inputStream: java.io.InputStream) {
        viewModelScope.launch {
            try {
                _uiState.value = DictionaryUiState.Loading
                
                val reader = CSVReader(InputStreamReader(inputStream))
                val words = reader.readAll().drop(1).map { row ->
                    Word(
                        english = removeQuotes(row[0]),
                        russian = removeQuotes(row[1]),
                        transcription = removeQuotes(row[2]),
                        example = removeQuotes(row[3])
                    )
                }
                
                val database = WordDatabase.getDatabase(context)
                database.wordDao().insertWords(words)
                
                _uiState.value = DictionaryUiState.Success(words.size)
            } catch (e: Exception) {
                _uiState.value = DictionaryUiState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }
}

sealed class DictionaryUiState {
    object Initial : DictionaryUiState()
    object Loading : DictionaryUiState()
    data class Success(val importedCount: Int) : DictionaryUiState()
    data class Error(val message: String) : DictionaryUiState()
} 