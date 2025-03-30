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

    init {
        loadWords()
        // Инициализация Text-to-Speech
        textToSpeech.language = Locale.US
    }

    override fun onCleared() {
        super.onCleared()
        textToSpeech.shutdown()
    }

    fun playWord(word: Word) {
        // Сначала пробуем воспроизвести из URL, если он есть в примере
        val soundUrl = extractSoundUrl(word.example)
        if (soundUrl != null) {
            playSoundFromUrl(soundUrl)
        } else {
            // Если URL нет, используем Text-to-Speech
            textToSpeech.speak(word.english, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun extractSoundUrl(example: String): String? {
        // Извлекаем URL звука из примера (формат: [sound:URL])
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
                    // Если не удалось воспроизвести звук, используем Text-to-Speech
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
                Log.e(TAG, "Error loading words", e)
                _uiState.value = DictionaryUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun filterWords(query: String) {
        searchQuery = query
        loadWords()
    }

    private fun cleanString(input: String): String {
        return input
            .replace("\"", "") // Удаляем двойные кавычки
            .replace("<br>", "") // Удаляем тег <br>
            .replace("<b>", "") // Удаляем тег <b>
            .replace("</b>", "") // Удаляем тег </b>
            .replace(Regex("<img src='[^']*'>"), "") // Удаляем теги img
            .replace(Regex("\\[sound:[^\\]]*\\]"), "") // Удаляем звуковые теги
            .replace(Regex("\\{\\{c1::[^}]*\\}\\}"), "") // Удаляем теги c1
            .trim() // Удаляем пробелы в начале и конце
    }

    fun importCsv(inputStream: InputStream) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting CSV import")
                _uiState.value = DictionaryUiState.Loading

                val parser = CSVParserBuilder()
                    .withSeparator(';')
                    .build()

                val reader = CSVReaderBuilder(InputStreamReader(inputStream))
                    .withCSVParser(parser)
                    .build()

                Log.d(TAG, "CSV Reader created")

                val allRows = reader.readAll()
                Log.d(TAG, "Read ${allRows.size} rows from CSV")

                if (allRows.isEmpty()) {
                    Log.w(TAG, "CSV file is empty")
                    _uiState.value = DictionaryUiState.Error("CSV файл пуст")
                    return@launch
                }

                // Обрабатываем строки
                val words = allRows.mapNotNull { row: Array<String> ->
                    try {
                        if (row.size >= 2) { // Нам нужны как минимум английское и русское слова
                            Log.d(TAG, "Processing row: ${row.joinToString(", ")}")
                            
                            // Находим транскрипцию - она обычно в формате /ˈtekst/ или [tekst]
                            val transcriptionIndex = row.indexOfFirst { field: String ->
                                field.trim().matches(Regex("[\\[/].*[\\]/]"))
                            }
                            
                            // Если транскрипция не найдена, используем пустую строку
                            val transcription = if (transcriptionIndex != -1) {
                                cleanString(row[transcriptionIndex])
                            } else {
                                ""
                            }

                            // Ищем пример использования после транскрипции
                            val exampleIndex = if (transcriptionIndex != -1) {
                                transcriptionIndex + 1
                            } else {
                                3 // Если транскрипция не найдена, пробуем взять четвертое поле
                            }

                            Word(
                                english = cleanString(row[0]),
                                russian = cleanString(row[1]),
                                transcription = transcription,
                                example = cleanString(row.getOrNull(exampleIndex) ?: "")
                            )
                        } else {
                            Log.w(TAG, "Row has less than 2 fields: ${row.joinToString(", ")}")
                            null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing row: ${row.joinToString(", ")}", e)
                        null
                    }
                }

                Log.d(TAG, "Processed ${words.size} valid words")

                if (words.isEmpty()) {
                    Log.w(TAG, "No valid words found in CSV")
                    _uiState.value = DictionaryUiState.Error("Не найдено корректных слов в CSV файле")
                    return@launch
                }

                try {
                    Log.d(TAG, "Inserting words into database")
                    wordDao.insertWords(words)
                    Log.d(TAG, "Successfully inserted ${words.size} words")
                    loadWords() // Перезагружаем список слов
                } catch (e: Exception) {
                    Log.e(TAG, "Error inserting words into database", e)
                    _uiState.value = DictionaryUiState.Error("Ошибка при сохранении слов: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during CSV import", e)
                _uiState.value = DictionaryUiState.Error("Ошибка при импорте CSV: ${e.message}")
            }
        }
    }

    fun addWord(word: Word) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Adding new word: ${word.english}")
                wordDao.insertWord(word)
                loadWords() // Перезагружаем список слов
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
                loadWords() // Перезагружаем список слов
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
                loadWords() // Перезагружаем список слов
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