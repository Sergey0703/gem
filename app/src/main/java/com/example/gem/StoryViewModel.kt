package com.example.gem

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
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
    private var isRussian = false

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
        
        val prompt = if (!isRussian) {
            """
                Create a short story using these words: ${selectedWords.joinToString(", ")}.
                Additional context from user: $userPrompt
                
                Requirements:
                1. The story should be 3-4 paragraphs long
                2. Each selected word must be used at least once
                3. The story should be engaging and creative
                4. Make sure the story flows naturally and the words are used in context
            """.trimIndent()
        } else {
            """
                Создай короткую историю, используя эти слова: ${selectedWords.joinToString(", ")}.
                Дополнительный контекст от пользователя: $userPrompt
                
                Требования:
                1. История должна быть длиной 3-4 абзаца
                2. Каждое выбранное слово должно быть использовано хотя бы раз
                3. История должна быть увлекательной и креативной
                4. Убедись, что история течет естественно и слова используются в контексте
            """.trimIndent()
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = generativeModel.generateContent(prompt)
                response.text?.let { outputContent ->
                    _uiState.value = UiState.Success(outputContent, selectedWords, isRussian)
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage ?: "Failed to generate story")
            }
        }
    }

    fun toggleLanguage(currentPrompt: String) {
        isRussian = !isRussian
        generateStory(currentPrompt)
    }

    fun speakText(text: String) {
        if (isSpeaking) {
            textToSpeech?.stop()
        } else {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "story_utterance")
        }
        isSpeaking = !isSpeaking
    }

    fun speakWord(word: String) {
        textToSpeech?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "word_utterance")
    }

    fun getWordInfo(word: String): Triple<String, String, String> {
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

        val example = when (word.lowercase()) {
            "adventure" -> "Going on an adventure in the mountains."
            "mystery" -> "The mystery of the ancient tomb."
            "rainbow" -> "A beautiful rainbow after the rain."
            "dragon" -> "The dragon breathed fire."
            "treasure" -> "Finding pirate's treasure."
            "magic" -> "Performing magic tricks."
            "journey" -> "A long journey home."
            "forest" -> "Walking through the dark forest."
            "castle" -> "Living in a medieval castle."
            "ocean" -> "Swimming in the deep ocean."
            else -> "Example not found"
        }
        
        return Triple(transcription, translation, example)
    }

    override fun onCleared() {
        super.onCleared()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
} 