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

    private suspend fun generateStory(prompt: String) {
        try {
            _uiState.value = UiState.Loading
            Log.d(TAG, "Starting story generation")

            // Разбиваем слова на группы по 25 слов для оптимальной обработки
            val wordBatches = availableWords.chunked(25)
            var fullEnglishStory = ""
            var fullRussianStory = ""

            // Генерируем историю для каждой группы слов
            wordBatches.forEachIndexed { index, batch ->
                val batchPrompt = """
                    You are a creative storyteller. Create a story part using these ${batch.size} words: ${batch.joinToString(", ")}.
                    
                    Rules:
                    1. Use EVERY word from the list exactly once
                    2. Keep the story natural and engaging
                    3. Write a coherent story part that can connect with other parts
                    4. Format your response EXACTLY like this:
                    
                    ENGLISH:
                    [Your English story here]
                    
                    RUSSIAN:
                    [Your Russian translation here]
                    
                    Do not include any other text or explanations.
                """.trimIndent()

                Log.d(TAG, "Generating story part ${index + 1} of ${wordBatches.size}")
                val response = generativeModel.generateContent(batchPrompt)
                val text = response.text ?: throw Exception("Empty response from API")
                Log.d(TAG, "Received response from API for part ${index + 1}")
                Log.d(TAG, "Raw response: $text")

                // Извлекаем английский и русский текст
                val englishText = text.substringAfter("ENGLISH:", "")
                    .substringBefore("RUSSIAN:", "")
                    .trim()
                    .takeIf { it.isNotEmpty() }
                    ?: throw Exception("English text not found in response")
                
                val russianText = text.substringAfter("RUSSIAN:", "")
                    .trim()
                    .takeIf { it.isNotEmpty() }
                    ?: throw Exception("Russian text not found in response")

                Log.d(TAG, "English part length: ${englishText.length}")
                Log.d(TAG, "Russian part length: ${russianText.length}")
                Log.d(TAG, "English part: $englishText")
                Log.d(TAG, "Russian part: $russianText")

                fullEnglishStory += if (fullEnglishStory.isEmpty()) englishText else "\n\n$englishText"
                fullRussianStory += if (fullRussianStory.isEmpty()) russianText else "\n\n$russianText"

                // Обновляем состояние после каждой части
                _uiState.value = UiState.Success(
                    englishVersion = fullEnglishStory.trim(),
                    russianVersion = fullRussianStory.trim(),
                    selectedWords = availableWords,
                    isRussian = isRussian,
                    currentSpokenWord = ""
                )

                // Увеличиваем паузу между запросами для стабильности
                kotlinx.coroutines.delay(1000)
            }

            Log.d(TAG, "Final English story length: ${fullEnglishStory.length}")
            Log.d(TAG, "Final Russian story length: ${fullRussianStory.length}")

            // Финальное обновление состояния
            _uiState.value = UiState.Success(
                englishVersion = fullEnglishStory.trim(),
                russianVersion = fullRussianStory.trim(),
                selectedWords = availableWords,
                isRussian = isRussian,
                currentSpokenWord = ""
            )
            Log.d(TAG, "Story generation completed successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error generating story", e)
            _uiState.value = UiState.Error("Error generating story: ${e.localizedMessage}")
        }
    }

    fun toggleLanguage() {
        try {
            stopSpeaking() // Останавливаем воспроизведение при переключении языка
            
            val currentState = _uiState.value
            if (currentState is UiState.Success) {
                isRussian = !isRussian
                _uiState.value = currentState.copy(
                    isRussian = isRussian
                )
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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                generateStory(prompt)
            } catch (e: Exception) {
                Log.e(TAG, "Error in story generation", e)
                _uiState.value = UiState.Error("Error in story generation: ${e.localizedMessage}")
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