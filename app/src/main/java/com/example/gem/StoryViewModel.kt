package com.example.gem

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

class StoryViewModel : ViewModel() {
    private val _uiState: MutableStateFlow<UiState> = MutableStateFlow(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var textToSpeech: TextToSpeech? = null
    private var isSpeaking = false

    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = BuildConfig.apiKey
    )

    fun initializeTTS(context: Context) {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.US
            }
        }
    }

    fun generateStory(userPrompt: String) {
        _uiState.value = UiState.Loading

        // Выбираем 4 случайных слова
        val selectedWords = words.toList().shuffled().take(4)
        
        val prompt = """
            Create a short story using these words: ${selectedWords.joinToString(", ")}.
            Additional context from user: $userPrompt
            
            Requirements:
            1. The story should be 3-4 paragraphs long
            2. Each selected word must be used at least once
            3. The story should be engaging and creative
            4. Make sure the story flows naturally and the words are used in context
        """.trimIndent()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = generativeModel.generateContent(prompt)
                response.text?.let { outputContent ->
                    _uiState.value = UiState.Success(outputContent, selectedWords)
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage ?: "Failed to generate story")
            }
        }
    }

    fun speakText(text: String) {
        if (isSpeaking) {
            textToSpeech?.stop()
        } else {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "story_utterance")
        }
        isSpeaking = !isSpeaking
    }

    fun getWordInfo(word: String): Pair<String, String> {
        // Здесь можно добавить реальный API для получения транскрипции и перевода
        // Пока используем заглушку
        val transcription = when (word.lowercase()) {
            "adventure" -> "[ədˈventʃə]"
            "mystery" -> "[ˈmɪstəri]"
            "rainbow" -> "[ˈreɪnbəʊ]"
            "dragon" -> "[ˈdræɡən]"
            "treasure" -> "[ˈtreʒə]"
            "magic" -> "[ˈmædʒɪk]"
            "journey" -> "[ˈdʒɜːni]"
            "forest" -> "[ˈfɒrɪst]"
            "castle" -> "[ˈkɑːsl]"
            "ocean" -> "[ˈəʊʃn]"
            else -> "[$word]"
        }
        
        val translation = when (word.lowercase()) {
            "adventure" -> "Приключение"
            "mystery" -> "Тайна"
            "rainbow" -> "Радуга"
            "dragon" -> "Дракон"
            "treasure" -> "Сокровище"
            "magic" -> "Магия"
            "journey" -> "Путешествие"
            "forest" -> "Лес"
            "castle" -> "Замок"
            "ocean" -> "Океан"
            else -> "Перевод не найден"
        }
        
        return Pair(transcription, translation)
    }

    override fun onCleared() {
        super.onCleared()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
} 