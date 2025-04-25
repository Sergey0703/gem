// app/src/main/java/com/example/gem/StoryViewModel.kt

package com.example.gem

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gem.data.WordDao
import com.example.gem.domain.GenerateStoryUseCase
import com.example.gem.domain.TextToSpeechUseCase
import com.example.gem.domain.TranslateStoryUseCase
import com.example.gem.domain.WordInfoUseCase
import com.example.gem.domain.WordStorageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StoryViewModel @Inject constructor(
    private val wordDao: WordDao, // Keep for backward compatibility
    private val generateStoryUseCase: GenerateStoryUseCase,
    private val textToSpeechUseCase: TextToSpeechUseCase,
    private val translateStoryUseCase: TranslateStoryUseCase,
    private val wordInfoUseCase: WordInfoUseCase,
    private val wordStorageUseCase: WordStorageUseCase
) : ViewModel() {
    private val TAG = "StoryViewModel"

    // Keep state in ViewModel
    private val _uiState: MutableStateFlow<UiState> = MutableStateFlow(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Set up callbacks from use cases to update UI state
        textToSpeechUseCase.setStateUpdateCallback { newState ->
            updateState(newState)
        }
        generateStoryUseCase.setStateUpdateCallback { newState ->
            updateState(newState)
        }
        translateStoryUseCase.setStateUpdateCallback { newState ->
            updateState(newState)
        }
        wordInfoUseCase.setStateUpdateCallback { newState ->
            updateState(newState)
        }
    }

    // Public methods that delegate to use cases
    fun initializeTTS(context: Context) {
        textToSpeechUseCase.initialize(context, getCurrentState())
    }

    fun setSpeechRate(rate: Float) {
        textToSpeechUseCase.setSpeechRate(rate)
    }

    fun startStoryGeneration(prompt: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading()
            try {
                val startTime = System.currentTimeMillis()
                val result = generateStoryUseCase.generateStory(prompt)
                val endTime = System.currentTimeMillis()
                val generationTime = (endTime - startTime) / 1000.0

                // Update UI state with result
                _uiState.value = UiState.Success(
                    englishVersion = result.first,
                    englishDisplayVersion = result.second,
                    russianVersion = "",
                    russianDisplayVersion = "",
                    selectedWords = result.third,
                    generationTime = generationTime,
                    isSpeaking = false,
                    lastHighlightedSentence = "",
                    lastSpokenWordIndex = 0
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error generating story: ${e.message}", e)
                _uiState.value = UiState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    // Speech-related methods
    fun speakText(context: Context, text: String, highlightedSentence: String = "") {
        textToSpeechUseCase.speakText(context, text, highlightedSentence, getCurrentState())
    }

    fun speakTextWithHighlight(context: Context, text: String, highlightedSentence: String = "") {
        textToSpeechUseCase.speakTextWithHighlight(context, text, highlightedSentence, getCurrentState())
    }

    fun speakSelectedWords(context: Context, words: List<String>) {
        textToSpeechUseCase.speakSelectedWords(context, words, getCurrentState())
    }

    fun speakWord(context: Context, word: String) {
        textToSpeechUseCase.speakWord(context, word, getCurrentState())
    }

    fun stopSpeaking() {
        textToSpeechUseCase.stopSpeaking(getCurrentState())
    }

    // Language toggle
    fun toggleLanguage() {
        viewModelScope.launch {
            translateStoryUseCase.toggleLanguage(getCurrentState())
        }
    }

    // Word info retrieval
    fun getWordInfoAndUpdate(word: String, onResult: (Triple<String, String, String>) -> Unit) {
        wordInfoUseCase.getWordInfoAndUpdate(word, onResult)
    }

    // Helper method to update state
    private fun updateState(updater: (UiState) -> UiState) {
        _uiState.value = updater(_uiState.value)
    }

    // Helper to get current state safely
    private fun getCurrentState(): UiState {
        return _uiState.value
    }

    override fun onCleared() {
        textToSpeechUseCase.shutdown()
        super.onCleared()
    }
}