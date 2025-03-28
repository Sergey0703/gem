package com.example.gem

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*
import kotlinx.coroutines.withContext

class StoryViewModel : ViewModel() {
    private val TAG = "StoryViewModel"
    private val _uiState: MutableStateFlow<UiState> = MutableStateFlow(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var tts: TextToSpeech? = null
    private var isSpeaking = false
    private var isRussian = false
    private var currentSentenceIndex = 0
    private var sentences = listOf<String>()
    private var speechRate = 1.0f
    private var currentLanguage = "en"

    // Список из 300 слов для генерации истории
    private val availableWords = listOf(
        "adventure", "mystery", "rainbow", "dragon", "treasure", "magic", "journey", "forest", "castle", "ocean",
        "mountain", "river", "desert", "island", "beach", "cave", "bridge", "tower", "garden", "park",
        "city", "village", "house", "school", "library", "museum", "shop", "market", "restaurant", "cafe",
        "book", "pen", "paper", "map", "key", "door", "window", "chair", "table", "lamp",
        "clock", "phone", "computer", "camera", "radio", "television", "music", "song", "dance", "art",
        "color", "red", "blue", "green", "yellow", "purple", "orange", "pink", "brown", "black",
        "white", "gray", "gold", "silver", "bronze", "stone", "wood", "metal", "glass", "plastic",
        "flower", "tree", "grass", "leaf", "branch", "root", "seed", "fruit", "vegetable", "food",
        "water", "fire", "earth", "air", "sun", "moon", "star", "cloud", "rain", "snow",
        "wind", "storm", "thunder", "lightning", "fog", "mist", "ice", "frost", "dew", "steam",
        "bird", "fish", "cat", "dog", "horse", "lion", "tiger", "bear", "wolf", "fox",
        "rabbit", "deer", "elephant", "giraffe", "zebra", "monkey", "penguin", "owl", "eagle", "swan",
        "butterfly", "bee", "ant", "spider", "snake", "frog", "turtle", "crab", "shark", "whale",
        "person", "child", "parent", "friend", "teacher", "doctor", "artist", "writer", "singer", "dancer",
        "king", "queen", "prince", "princess", "knight", "wizard", "witch", "giant", "dwarf", "elf",
        "time", "day", "night", "morning", "evening", "spring", "summer", "autumn", "winter", "week",
        "month", "year", "hour", "minute", "second", "today", "tomorrow", "yesterday", "now", "later",
        "happy", "sad", "angry", "scared", "excited", "tired", "sleepy", "hungry", "thirsty", "cold",
        "hot", "warm", "cool", "wet", "dry", "clean", "dirty", "new", "old", "young",
        "big", "small", "tall", "short", "long", "wide", "narrow", "heavy", "light", "fast",
        "slow", "loud", "quiet", "bright", "dark", "sweet", "sour", "salty", "spicy", "fresh",
        "walk", "run", "jump", "swim", "fly", "climb", "dance", "sing", "talk", "laugh",
        "cry", "smile", "frown", "wave", "nod", "shake", "point", "touch", "feel", "see",
        "hear", "smell", "taste", "think", "know", "learn", "remember", "forget", "understand", "believe",
        "want", "need", "like", "love", "hate", "hope", "wish", "dream", "plan", "try",
        "start", "stop", "finish", "begin", "end", "continue", "change", "grow", "help", "work",
        "play", "study", "read", "write", "draw", "paint", "build", "make", "create", "design",
        "find", "lose", "get", "give", "take", "bring", "carry", "hold", "drop", "throw",
        "catch", "kick", "hit", "push", "pull", "lift", "move", "turn", "bend", "stretch"
    )

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = BuildConfig.translationApiKey.also { key ->
            Log.d(TAG, "API Key length: ${key.length}")
            if (key.isEmpty()) {
                Log.e(TAG, "API Key is empty!")
            }
        }
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

    private suspend fun generateStory(prompt: String, maxAttempts: Int = 5): Triple<String, String, List<String>> {
        try {
            _uiState.value = UiState.Loading(
                maxAttempts = maxAttempts,
                totalWords = availableWords.size
            )
            Log.d(TAG, "Starting story generation")

            var attempt = 1
            var lastMissingWords = availableWords
            var englishStory = ""
            val errorThreshold = availableWords.size / 10 // 10% от общего количества слов

            while (attempt <= maxAttempts) {
                _uiState.value = UiState.Loading(
                    attempt = attempt,
                    maxAttempts = maxAttempts,
                    totalWords = availableWords.size
                )

                val storyPrompt = if (attempt == 1) {
                    """
                        You are a creative storyteller. Create an engaging and coherent story that MUST use ALL of these words: ${availableWords.joinToString(", ")}.
                        
                        Rules:
                        1. Use EVERY word from the list exactly once - this is the most important rule
                        2. Mark each used word with asterisks like this: *word*
                        3. Create a story that flows naturally despite using all required words
                        4. Break the story into logical paragraphs for readability
                        5. Make sure the story has a clear beginning, middle, and end
                        6. Focus on making connections between words to create a coherent narrative
                        
                        Important:
                        - Every word from the list MUST be used exactly once
                        - Only mark the exact words with asterisks
                        - Write only the story, no additional text or explanations
                        - Make sure the story makes sense and reads naturally
                    """.trimIndent()
                } else {
                    """
                        Please revise the story to include these missing words: ${lastMissingWords.joinToString(", ")}.
                        
                        Previous attempt: 
                        $englishStory
                        
                        Rules:
                        1. Use EVERY missing word exactly once while keeping the story coherent
                        2. Mark the used words with asterisks like this: *word*
                        3. You can modify the existing story to naturally incorporate the missing words
                        4. Keep the story's structure and main ideas, but expand it to include all words
                        5. Ensure the story flows naturally and makes sense
                        
                        Important:
                        - Every word from the missing list MUST be used
                        - Only mark the exact words with asterisks
                        - Write the complete story, not just the additions
                    """.trimIndent()
                }

                Log.d(TAG, "Generating story - Attempt $attempt of $maxAttempts")
                val response = generativeModel.generateContent(storyPrompt)
                englishStory = response.text?.trim() ?: throw Exception("Empty response from API")
                
                // Проверяем использование всех слов
                val usedWords = availableWords.filter { word ->
                    englishStory.contains("*$word*", ignoreCase = true)
                }
                
                // Не все слова использованы, готовимся к следующей попытке
                lastMissingWords = availableWords.filter { word ->
                    !englishStory.contains("*$word*", ignoreCase = true)
                }

                // Обновляем состояние с прогрессом
                _uiState.value = UiState.Loading(
                    attempt = attempt,
                    maxAttempts = maxAttempts,
                    usedWordsCount = usedWords.size,
                    totalWords = availableWords.size,
                    storyLength = englishStory.length,
                    missingWords = lastMissingWords
                )
                
                Log.d(TAG, "Story generated - Attempt $attempt")
                Log.d(TAG, "English story length: ${englishStory.length}")
                Log.d(TAG, "Used words count: ${usedWords.size}/${availableWords.size}")
                
                if (lastMissingWords.isEmpty()) {
                    // Все слова использованы, обновляем состояние и завершаем
                    _uiState.value = UiState.Success(
                        englishVersion = englishStory,
                        russianVersion = "",
                        selectedWords = availableWords,
                        isRussian = false,
                        currentSpokenWord = "",
                        generationTime = 0.0
                    )
                    Log.d(TAG, "Story generation completed successfully")
                    return Triple(englishStory, "", availableWords)
                } else {
                    Log.w(TAG, "Missing ${lastMissingWords.size} words in attempt $attempt: ${lastMissingWords.joinToString(", ")}")
                    
                    if (attempt == maxAttempts && lastMissingWords.size >= errorThreshold) {
                        throw Exception("Failed to use enough words after $maxAttempts attempts. Missing ${lastMissingWords.size} words (threshold: $errorThreshold).")
                    } else if (attempt == maxAttempts) {
                        // Если это последняя попытка, но пропущенных слов меньше порога, используем текущую версию
                        _uiState.value = UiState.Success(
                            englishVersion = englishStory,
                            russianVersion = "",
                            selectedWords = availableWords,
                            isRussian = false,
                            currentSpokenWord = "",
                            generationTime = 0.0
                        )
                        Log.d(TAG, "Story generation completed with ${lastMissingWords.size} missing words (below threshold)")
                        return Triple(englishStory, "", availableWords)
                    }
                }
                
                attempt++
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating story", e)
            _uiState.value = UiState.Error("Error generating story: ${e.localizedMessage}")
            return Triple("", "", listOf())
        }
        return Triple("", "", listOf())
    }

    fun toggleLanguage() {
        val currentState = _uiState.value
        if (currentState is UiState.Success) {
            _uiState.value = currentState.copy(isTranslating = true)
            viewModelScope.launch {
                // Если переключаемся на русский и перевода еще нет
                if (!currentState.isRussian && currentState.russianVersion.isEmpty()) {
                    try {
                        val translationPrompt = """
                            Translate this story to Russian. Keep the same formatting and paragraph breaks.
                            Mark the translated equivalents of marked words with asterisks.
                            Do not add any additional formatting or special characters.
                            
                            Story to translate:
                            ${currentState.englishVersion}
                            
                            Provide only the Russian translation, no additional text.
                        """.trimIndent()

                        Log.d(TAG, "Requesting translation")
                        val response = generativeModel.generateContent(translationPrompt)
                        var russianStory = response.text?.trim() ?: throw Exception("Empty translation response")
                        
                        // Очищаем текст от лишних обратных слешей
                        russianStory = russianStory.replace("\\", "")
                        
                        Log.d(TAG, "Translation received")
                        Log.d(TAG, "Russian story length: ${russianStory.length}")

                        _uiState.value = currentState.copy(
                            russianVersion = russianStory,
                            isRussian = true,
                            isTranslating = false
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error translating story", e)
                        _uiState.value = UiState.Error("Error translating story: ${e.localizedMessage}")
                    }
                } else {
                    // Просто переключаем язык, если перевод уже есть
                    delay(500) // Небольшая задержка для анимации
                    _uiState.value = currentState.copy(
                        isRussian = !currentState.isRussian,
                        isTranslating = false
                    )
                }
            }
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

    suspend fun getWordInfo(word: String): Triple<String, String, String> {
        val prompt = """
            For the English word "$word", provide:
            1. The International Phonetic Alphabet (IPA) transcription in square brackets
            2. The Russian translation
            3. A simple example sentence in English
            
            Format your response exactly like this:
            TRANSCRIPTION: [phonetic symbols here]
            TRANSLATION: Russian translation here
            EXAMPLE: Example sentence here
            
            For example, if the word is "cat":
            TRANSCRIPTION: [kæt]
            TRANSLATION: кошка
            EXAMPLE: I have a black cat.
            
            Make sure the transcription uses proper IPA symbols and is enclosed in square brackets.
        """.trimIndent()
        
        return try {
            val response = generativeModel.generateContent(prompt).text?.trim() ?: ""
            
            // Parse the response
            val transcription = response.substringAfter("TRANSCRIPTION: ")
                .substringBefore("\n")
                .trim()
            
            val translation = response.substringAfter("TRANSLATION: ")
                .substringBefore("\n")
                .trim()
            
            val example = response.substringAfter("EXAMPLE: ")
                .trim()
            
            Triple(transcription, translation, example)
        } catch (e: Exception) {
            Log.e("StoryViewModel", "Error getting word info: ${e.message}")
            Triple("[${word}]", "Ошибка перевода", "Error getting example")
        }
    }

    fun getWordInfoAndUpdate(word: String, onResult: (Triple<String, String, String>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = getWordInfo(word)
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
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

    fun startStoryGeneration(prompt: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading()
            try {
                val startTime = System.currentTimeMillis()
                val result = generateStory(prompt)
                val endTime = System.currentTimeMillis()
                val generationTime = (endTime - startTime) / 1000.0

                _uiState.value = UiState.Success(
                    englishVersion = result.first,
                    russianVersion = result.second,
                    selectedWords = result.third,
                    generationTime = generationTime
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    fun speakTextWithHighlight(context: Context, text: String) {
        viewModelScope.launch {
            val sentences = text.split(Regex("[.!?]+\\s+"))
            
            for (sentence in sentences) {
                if (sentence.isBlank()) continue
                
                // Update UI with current sentence
                _uiState.value = when (val currentState = _uiState.value) {
                    is UiState.Success -> currentState.copy(
                        currentSpokenWord = sentence.trim()
                    )
                    else -> currentState
                }
                
                // Speak the sentence
                tts?.let { tts ->
                    tts.speak(sentence.trim(), TextToSpeech.QUEUE_FLUSH, null, null)
                    // Wait for the sentence to be spoken
                    delay(sentence.length * 90L) // Approximate timing based on text length
                }
            }
            
            // Clear highlighted text when done
            _uiState.value = when (val currentState = _uiState.value) {
                is UiState.Success -> currentState.copy(
                    currentSpokenWord = ""
                )
                else -> currentState
            }
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