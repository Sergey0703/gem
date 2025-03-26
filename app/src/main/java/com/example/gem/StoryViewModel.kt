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
                Create a bilingual story (English and Russian) using these words: ${selectedWords.joinToString(", ")}.
                Additional context from user: $userPrompt
                
                Requirements:
                1. First, provide translations for all the selected words in this format:
                   Word: [English word] - [Russian translation] - [Transcription]
                2. Then create a story that is 3-4 paragraphs long
                3. Provide the story in both English and Russian (paragraph by paragraph)
                4. Each selected word must be used at least once
                5. The story should be engaging and creative
                6. Make sure the story flows naturally and the words are used in context
                
                Format the response like this:
                TRANSLATIONS:
                [word translations as specified above]
                
                ENGLISH STORY:
                [English version of the story]
                
                RUSSIAN STORY:
                [Russian version of the story]
            """.trimIndent()
        } else {
            """
                Создай двуязычную историю (на русском и английском) используя эти слова: ${selectedWords.joinToString(", ")}.
                Дополнительный контекст от пользователя: $userPrompt
                
                Требования:
                1. Сначала предоставь переводы всех выбранных слов в таком формате:
                   Слово: [Английское слово] - [Русский перевод] - [Транскрипция]
                2. Затем создай историю длиной 3-4 абзаца
                3. Предоставь историю на обоих языках (абзац за абзацем)
                4. Каждое выбранное слово должно быть использовано хотя бы раз
                5. История должна быть увлекательной и креативной
                6. Убедись, что история течет естественно и слова используются в контексте
                
                Форматируй ответ так:
                ПЕРЕВОДЫ:
                [переводы слов как указано выше]
                
                АНГЛИЙСКАЯ ВЕРСИЯ:
                [История на английском]
                
                РУССКАЯ ВЕРСИЯ:
                [История на русском]
            """.trimIndent()
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = generativeModel.generateContent(prompt)
                response.text?.let { outputContent ->
                    // Разбираем ответ на секции по маркерам
                    val translationsStart = outputContent.indexOf("TRANSLATIONS:")
                    val englishStart = outputContent.indexOf("ENGLISH STORY:")
                    val russianStart = outputContent.indexOf("RUSSIAN STORY:")
                    
                    val translations = outputContent.substring(
                        translationsStart,
                        englishStart
                    ).trim()
                    
                    val englishStory = outputContent.substring(
                        englishStart,
                        russianStart
                    ).replace("ENGLISH STORY:", "").trim()
                    
                    val russianStory = outputContent.substring(
                        russianStart
                    ).replace("RUSSIAN STORY:", "").trim()
                    
                    // Сохраняем все версии текста
                    _uiState.value = UiState.Success(
                        outputText = englishStory, // Показываем изначально английскую версию
                        selectedWords = selectedWords,
                        isRussian = isRussian,
                        translations = translations,
                        englishVersion = englishStory,
                        russianVersion = russianStory
                    )
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage ?: "Failed to generate story")
            }
        }
    }

    fun toggleLanguage(currentPrompt: String) {
        val currentState = _uiState.value
        if (currentState is UiState.Success) {
            isRussian = !isRussian
            
            // Просто переключаем между сохраненными версиями
            _uiState.value = currentState.copy(
                outputText = if (isRussian) currentState.russianVersion else currentState.englishVersion,
                isRussian = isRussian
            )
        } else {
            // Если еще нет сгенерированного текста, генерируем новый
            isRussian = !isRussian
            generateStory(currentPrompt)
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

    fun speakWord(word: String) {
        textToSpeech?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "word_utterance")
    }

    fun getWordInfo(word: String): Triple<String, String, String> {
        // Получаем текущее состояние
        val currentState = _uiState.value
        
        // Если у нас есть переводы от Gemini
        if (currentState is UiState.Success && currentState.translations.isNotEmpty()) {
            // Ищем перевод слова в переводах от Gemini
            val wordPattern = "(?i)Word: $word - ([^-]+) - \\[([^\\]]+)\\]".toRegex()
            val match = wordPattern.find(currentState.translations)
            
            if (match != null) {
                val (translation, transcription) = match.destructured
                return Triple("[$transcription]", translation.trim(), "Example not found")
            }
        }
        
        // Если перевод не найден в Gemini, используем предопределенные переводы
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