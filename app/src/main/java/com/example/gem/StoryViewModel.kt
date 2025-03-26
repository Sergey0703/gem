package com.example.gem

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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

    private var tts: TextToSpeech? = null
    private var isSpeaking = false
    private var isRussian = false
    private var currentSentenceIndex = 0
    private var sentences = listOf<String>()
    private var speechRate = 1.0f

    // Добавляем предопределенный список слов
    private val availableWords = listOf(
        "adventure",
        "mystery",
        "rainbow",
        "dragon",
        "treasure",
        "magic",
        "journey",
        "forest",
        "castle",
        "ocean"
    )

    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = BuildConfig.apiKey
    )

    fun setSpeechRate(rate: Float) {
        speechRate = rate
        tts?.setSpeechRate(rate)
    }

    fun initializeTTS(context: Context) {
        if (tts == null) {
            try {
                tts = TextToSpeech(context) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        tts?.language = Locale.US
                        tts?.setSpeechRate(speechRate)
                        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String) {
                                viewModelScope.launch {
                                    if (utteranceId.startsWith("sentence_") && currentSentenceIndex < sentences.size) {
                                        _uiState.value = (_uiState.value as? UiState.Success)?.copy(
                                            currentSpokenWord = sentences[currentSentenceIndex]
                                        ) ?: _uiState.value
                                    }
                                }
                            }

                            override fun onDone(utteranceId: String) {
                                if (utteranceId.startsWith("sentence_") && isSpeaking && currentSentenceIndex < sentences.size - 1) {
                                    currentSentenceIndex++
                                    speakNextSentence()
                                } else {
                                    isSpeaking = false
                                    currentSentenceIndex = 0
                                    viewModelScope.launch {
                                        _uiState.value = (_uiState.value as? UiState.Success)?.copy(
                                            currentSpokenWord = ""
                                        ) ?: _uiState.value
                                    }
                                }
                            }

                            override fun onError(utteranceId: String) {
                                isSpeaking = false
                                currentSentenceIndex = 0
                                viewModelScope.launch {
                                    _uiState.value = (_uiState.value as? UiState.Success)?.copy(
                                        currentSpokenWord = ""
                                    ) ?: _uiState.value
                                }
                            }
                        })
                    } else {
                        // Обработка ошибки инициализации TTS
                        _uiState.value = UiState.Error("Failed to initialize text-to-speech")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Error initializing text-to-speech: ${e.localizedMessage}")
            }
        }
    }

    fun generateStory(userPrompt: String) {
        _uiState.value = UiState.Loading

        // Выбираем 4 случайных слова из доступного списка
        val selectedWords = availableWords.shuffled().take(4)
        
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
                7. After the story, provide a vocabulary list of ALL words used in the story (not just the selected ones)
                
                Format the response like this:
                TRANSLATIONS:
                [word translations as specified above]
                
                ENGLISH STORY:
                [English version of the story]
                
                RUSSIAN STORY:
                [Russian version of the story]
                
                VOCABULARY:
                [List all unique words from the story in format: English word - Russian translation - [transcription]]
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
                7. После истории предоставь словарь ВСЕХ использованных слов (не только выбранных)
                
                Форматируй ответ так:
                ПЕРЕВОДЫ:
                [переводы слов как указано выше]
                
                АНГЛИЙСКАЯ ВЕРСИЯ:
                [История на английском]
                
                РУССКАЯ ВЕРСИЯ:
                [История на русском]
                
                СЛОВАРЬ:
                [Список всех уникальных слов из истории в формате: Английское слово - Русский перевод - [транскрипция]]
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
                    val vocabularyStart = outputContent.indexOf(if (!isRussian) "VOCABULARY:" else "СЛОВАРЬ:")
                    
                    val translations = if (translationsStart >= 0 && englishStart > translationsStart) {
                        outputContent.substring(translationsStart, englishStart).trim()
                    } else {
                        ""
                    }
                    
                    val englishStory = if (englishStart >= 0 && russianStart > englishStart) {
                        outputContent.substring(englishStart, russianStart)
                            .replace("ENGLISH STORY:", "").trim()
                    } else {
                        ""
                    }
                    
                    val russianStory = if (russianStart >= 0 && vocabularyStart > russianStart) {
                        outputContent.substring(russianStart, vocabularyStart)
                            .replace("RUSSIAN STORY:", "").trim()
                    } else {
                        ""
                    }
                    
                    val vocabulary = if (vocabularyStart >= 0) {
                        outputContent.substring(vocabularyStart)
                            .replace(if (!isRussian) "VOCABULARY:" else "СЛОВАРЬ:", "").trim()
                    } else {
                        ""
                    }
                    
                    // Объединяем переводы выбранных слов и словарь
                    val allTranslations = """
                        $translations
                        
                        $vocabulary
                    """.trimIndent()
                    
                    // Сохраняем все версии текста
                    _uiState.value = UiState.Success(
                        outputText = englishStory, // Показываем изначально английскую версию
                        selectedWords = selectedWords,
                        isRussian = isRussian,
                        translations = allTranslations,
                        englishVersion = englishStory,
                        russianVersion = russianStory
                    )
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage ?: "Failed to generate story")
            }
        }
    }

    fun toggleLanguage(prompt: String) {
        try {
            stopSpeaking() // Останавливаем воспроизведение при переключении языка
            
            val currentState = _uiState.value
            if (currentState is UiState.Success) {
                isRussian = !isRussian
                if (isRussian && currentState.russianVersion.isNotEmpty()) {
                    _uiState.value = currentState.copy(
                        outputText = currentState.russianVersion,
                        isRussian = true
                    )
                } else if (!isRussian && currentState.englishVersion.isNotEmpty()) {
                    _uiState.value = currentState.copy(
                        outputText = currentState.englishVersion,
                        isRussian = false
                    )
                } else {
                    generateStory(prompt)
                }
            }
        } catch (e: Exception) {
            _uiState.value = UiState.Error("Error toggling language: ${e.localizedMessage}")
        }
    }

    fun speakText(context: Context, text: String) {
        try {
            initializeTTS(context)
            
            if (isSpeaking) {
                stopSpeaking()
                return
            }

            if (text.isBlank()) {
                return
            }

            sentences = text.split(Regex("(?<=[.!?])\\s+"))
                .filter { it.isNotBlank() }
                .map { it.trim() }

            if (sentences.isNotEmpty()) {
                isSpeaking = true
                currentSentenceIndex = 0
                speakNextSentence()
            }
        } catch (e: Exception) {
            _uiState.value = UiState.Error("Error starting speech: ${e.localizedMessage}")
        }
    }

    private fun speakNextSentence() {
        try {
            if (currentSentenceIndex < sentences.size) {
                val sentence = sentences[currentSentenceIndex]
                tts?.speak(sentence, TextToSpeech.QUEUE_FLUSH, null, "sentence_$currentSentenceIndex")
            }
        } catch (e: Exception) {
            _uiState.value = UiState.Error("Error speaking sentence: ${e.localizedMessage}")
        }
    }

    fun speakWord(context: Context, word: String) {
        initializeTTS(context)
        stopSpeaking()
        tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "single_word")
    }

    private fun stopSpeaking() {
        try {
            tts?.stop()
            isSpeaking = false
            currentSentenceIndex = 0
            viewModelScope.launch {
                _uiState.value = (_uiState.value as? UiState.Success)?.copy(
                    currentSpokenWord = ""
                ) ?: _uiState.value
            }
        } catch (e: Exception) {
            _uiState.value = UiState.Error("Error stopping speech: ${e.localizedMessage}")
        }
    }

    fun getWordInfo(word: String): Triple<String, String, String> {
        // Получаем текущее состояние
        val currentState = _uiState.value
        
        // Если у нас есть переводы от Gemini
        if (currentState is UiState.Success) {
            // Ищем слово в общем списке переводов (включая словарь)
            // Если текст на русском, ищем русское слово для получения английского перевода
            val wordPattern = if (isRussian) {
                // Ищем формат "английское - русское" где русское слово совпадает с нашим
                "(?i)([\\w\\s]+) - $word - \\[([^\\]]+)\\]".toRegex()
            } else {
                // Ищем формат "слово - перевод" где слово совпадает с нашим
                "(?i)(?:Word: )?$word - ([^-]+) - \\[([^\\]]+)\\]".toRegex()
            }
            
            val match = wordPattern.find(currentState.translations)
            
            if (match != null) {
                if (isRussian) {
                    // Если текст на русском, то первая группа - английское слово, вторая - транскрипция
                    val (englishWord, transcription) = match.destructured
                    return Triple("[$transcription]", englishWord.trim(), "")
                } else {
                    // Если текст на английском, то первая группа - русский перевод, вторая - транскрипция
                    val (translation, transcription) = match.destructured
                    return Triple("[$transcription]", translation.trim(), "")
                }
            }
        }
        
        // Если перевод не найден в Gemini, используем предопределенные переводы
        val (transcription, translation) = if (isRussian) {
            // Если текст на русском, ищем английский эквивалент
            when (word.lowercase()) {
                "приключение" -> Pair("[ədˈventʃə]", "adventure")
                "тайна" -> Pair("[ˈmɪstəri]", "mystery")
                "радуга" -> Pair("[ˈreɪnbəʊ]", "rainbow")
                "дракон" -> Pair("[ˈdræɡən]", "dragon")
                "сокровище" -> Pair("[ˈtreʒə]", "treasure")
                "магия" -> Pair("[ˈmædʒɪk]", "magic")
                "путешествие" -> Pair("[ˈdʒɜːni]", "journey")
                "лес" -> Pair("[ˈfɒrɪst]", "forest")
                "замок" -> Pair("[ˈkɑːsl]", "castle")
                "океан" -> Pair("[ˈəʊʃn]", "ocean")
                else -> Pair("[$word]", "Translation not found")
            }
        } else {
            // Если текст на английском, ищем русский перевод
            when (word.lowercase()) {
                "adventure" -> Pair("[ədˈventʃə]", "Приключение")
                "mystery" -> Pair("[ˈmɪstəri]", "Тайна")
                "rainbow" -> Pair("[ˈreɪnbəʊ]", "Радуга")
                "dragon" -> Pair("[ˈdræɡən]", "Дракон")
                "treasure" -> Pair("[ˈtreʒə]", "Сокровище")
                "magic" -> Pair("[ˈmædʒɪk]", "Магия")
                "journey" -> Pair("[ˈdʒɜːni]", "Путешествие")
                "forest" -> Pair("[ˈfɒrɪst]", "Лес")
                "castle" -> Pair("[ˈkɑːsl]", "Замок")
                "ocean" -> Pair("[ˈəʊʃn]", "Океан")
                else -> Pair("[$word]", "Перевод не найден")
            }
        }
        
        return Triple(transcription, translation, "")
    }

    fun speakTextSmooth(context: Context, text: String) {
        try {
            initializeTTS(context)
            
            if (isSpeaking) {
                stopSpeaking()
                return
            }

            if (text.isBlank()) {
                return
            }

            isSpeaking = true
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "smooth_reading")
        } catch (e: Exception) {
            _uiState.value = UiState.Error("Error starting smooth speech: ${e.localizedMessage}")
        }
    }

    override fun onCleared() {
        try {
            stopSpeaking()
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            // Игнорируем ошибки при закрытии
        }
        super.onCleared()
    }
} 