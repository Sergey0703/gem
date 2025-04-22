package com.example.gem

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gem.data.Word
import com.example.gem.data.WordDao
import com.google.ai.client.generativeai.GenerativeModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class StoryViewModel @Inject constructor(
    private val wordDao: WordDao
) : ViewModel() {
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
    private var isSmoothReading = false

    // Флаг для отслеживания режима чтения
    private var isReadingWords = false

    // Для режима чтения слов
    private var currentWordIndex = 0
    private var lastSpokenWordIndex = 0
    private var selectedWords = listOf<String>()

    // Переменная для сохранения последнего прочитанного предложения
    private var lastHighlightedSentence = ""

    // Специальный разделитель предложений
    private val SENTENCE_SEPARATOR = "<<SENTENCE_END>>"

    // Количество слов, которое будет использовано для каждой истории
    private val STORY_WORDS_COUNT = 300
    // Максимальное количество слов, которое будет получено из базы данных
    private val MAX_WORDS_TO_FETCH = 300

    private val generativeModel = GenerativeModel(
        modelName = "gemini-2.0-flash",
        apiKey = BuildConfig.API_KEY.also { key ->
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
                                    if (utteranceId.startsWith("sentence_") && currentSentenceIndex < sentences.size && !isReadingWords) {
                                        _uiState.value = (_uiState.value as? UiState.Success)?.copy(
                                            currentSpokenWord = sentences[currentSentenceIndex],
                                            isSpeaking = true
                                        ) as UiState ?: _uiState.value
                                    }
                                    // Обработка для режима слов не нужна здесь, так как она выполняется в speakSelectedWords
                                }
                            }

                            override fun onDone(utteranceId: String) {
                                if (isReadingWords) {
                                    // Для режима чтения слов отдельная логика не нужна здесь,
                                    // так как она выполняется в speakSelectedWords
                                    return
                                } else if (utteranceId.startsWith("sentence_") && isSpeaking && currentSentenceIndex < sentences.size - 1) {
                                    // Логика для последовательного чтения предложений текста
                                    currentSentenceIndex++
                                    speakNextSentence()
                                } else if (utteranceId.startsWith("sentence_")) {
                                    // Сохраняем последнее произнесенное предложение
                                    if (currentSentenceIndex < sentences.size) {
                                        lastHighlightedSentence = sentences[currentSentenceIndex]
                                    }

                                    isSpeaking = false
                                    currentSentenceIndex = 0
                                    viewModelScope.launch {
                                        _uiState.value = (_uiState.value as? UiState.Success)?.copy(
                                            currentSpokenWord = "",
                                            lastHighlightedSentence = lastHighlightedSentence,
                                            isSpeaking = false
                                        ) as UiState ?: _uiState.value
                                    }
                                }
                            }

                            override fun onError(utteranceId: String) {
                                isSpeaking = false
                                isReadingWords = false
                                currentSentenceIndex = 0
                                viewModelScope.launch {
                                    _uiState.value = (_uiState.value as? UiState.Success)?.copy(
                                        currentSpokenWord = "",
                                        isSpeaking = false
                                    ) as UiState ?: _uiState.value
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

    private suspend fun getWordsForStory(): List<String> {
        try {
            // Получаем слова из базы данных, отсортированные по дате последнего использования (сначала с NULL)
            val words = wordDao.getWordsToReview(Date(), MAX_WORDS_TO_FETCH).first()

            if (words.isEmpty()) {
                throw Exception("Dictionary is empty. Please add some words first.")
            }

            // Преобразуем список Word в список строк английских слов
            val englishWords = words.map { it.english }

            // Перемешиваем список слов для разнообразия историй
            val shuffledWords = englishWords.shuffled()

            // Выбираем подмножество слов для истории
            val selectedWords = if (shuffledWords.size <= STORY_WORDS_COUNT) {
                shuffledWords
            } else {
                shuffledWords.subList(0, STORY_WORDS_COUNT)
            }

            return selectedWords
        } catch (e: Exception) {
            Log.e(TAG, "Error getting words for story", e)
            throw e
        }
    }

    private suspend fun generateStory(prompt: String, maxAttempts: Int = 5): Triple<String, String, List<String>> {
        try {
            // Получаем слова из словаря
            val availableWords = getWordsForStory()

            _uiState.value = UiState.Loading(
                maxAttempts = maxAttempts,
                totalWords = availableWords.size
            )
            Log.d(TAG, "Starting story generation with words: ${availableWords.joinToString(", ")}")

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
                        7. EXTREMELY IMPORTANT: After each complete sentence (ending with period, exclamation mark, or question mark), insert the exact text "$SENTENCE_SEPARATOR". 
                           DO NOT abbreviate or modify this separator in any way. 
                           DO NOT skip adding this separator after ANY sentence.
                           Include this separator even at paragraph breaks.
                        
                        Example format:
                        This is the first sentence.$SENTENCE_SEPARATOR This is the second sentence.$SENTENCE_SEPARATOR
                        
                        Important:
                        - Every word from the list MUST be used exactly once
                        - Only mark the exact words with asterisks
                        - Add "$SENTENCE_SEPARATOR" after EVERY sentence
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
                        6. EXTREMELY IMPORTANT: After each complete sentence (ending with period, exclamation mark, or question mark), insert the exact text "$SENTENCE_SEPARATOR". 
                           DO NOT abbreviate or modify this separator in any way. 
                           DO NOT skip adding this separator after ANY sentence.
                           Include this separator even at paragraph breaks.
                        
                        Example format:
                        This is the first sentence.$SENTENCE_SEPARATOR This is the second sentence.$SENTENCE_SEPARATOR
                        
                        Important:
                        - Every word from the missing list MUST be used
                        - Only mark the exact words with asterisks
                        - Add "$SENTENCE_SEPARATOR" after EVERY sentence
                        - Write the complete story, not just the additions
                    """.trimIndent()
                }

                Log.d(TAG, "Generating story - Attempt $attempt of $maxAttempts")
                val response = generativeModel.generateContent(storyPrompt)
                englishStory = response.text?.trim() ?: throw Exception("Empty response from API")

                Log.d(TAG, "Raw API response first 500 chars: ${englishStory.take(500)}")
                Log.d(TAG, "Contains separators: ${englishStory.contains(SENTENCE_SEPARATOR)}")
                Log.d(TAG, "Count of separators: ${englishStory.split(SENTENCE_SEPARATOR).size - 1}")

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
                        englishDisplayVersion = cleanTextForUI(cleanTextForDisplay(englishStory)),
                        russianVersion = "",
                        russianDisplayVersion = "",
                        selectedWords = availableWords,
                        isRussian = false,
                        currentSpokenWord = "",
                        lastHighlightedSentence = "",
                        generationTime = 0.0,
                        isSpeaking = false,
                        lastSpokenWordIndex = 0
                    )
                    Log.d(TAG, "Story generation completed successfully")

                    // Обновляем даты использования слов
                    updateWordUsageDates(availableWords)

                    return Triple(englishStory, "", availableWords)
                } else {
                    Log.w(TAG, "Missing ${lastMissingWords.size} words in attempt $attempt: ${lastMissingWords.joinToString(", ")}")

                    if (attempt == maxAttempts && lastMissingWords.size >= errorThreshold) {
                        throw Exception("Failed to use enough words after $maxAttempts attempts. Missing ${lastMissingWords.size} words (threshold: $errorThreshold).")
                    } else if (attempt == maxAttempts) {
                        // Если это последняя попытка, но пропущенных слов меньше порога, используем текущую версию
                        _uiState.value = UiState.Success(
                            englishVersion = englishStory,
                            englishDisplayVersion = cleanTextForUI(cleanTextForDisplay(englishStory)),
                            russianVersion = "",
                            russianDisplayVersion = "",
                            selectedWords = availableWords,
                            isRussian = false,
                            currentSpokenWord = "",
                            lastHighlightedSentence = "",
                            generationTime = 0.0,
                            isSpeaking = false,
                            lastSpokenWordIndex = 0
                        )
                        Log.d(TAG, "Story generation completed with ${lastMissingWords.size} missing words (below threshold)")

                        // Обновляем даты использования только для использованных слов
                        val usedWordsSet = usedWords.toSet()
                        updateWordUsageDates(usedWordsSet.toList())

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

    // Обновляем даты последнего использования слов
    private suspend fun updateWordUsageDates(words: List<String>) {
        try {
            withContext(Dispatchers.IO) {
                // Получаем все слова из базы данных
                val allWords = wordDao.getAllWords().first()
                // Создаем карту английское слово -> id
                val wordIdMap = allWords.associate { it.english.lowercase() to it.id }

                // Для каждого слова в истории обновляем дату последнего использования
                words.forEach { word ->
                    wordIdMap[word.lowercase()]?.let { wordId ->
                        wordDao.updateWordUsage(wordId, Date())
                        Log.d(TAG, "Updated usage date for word: $word (ID: $wordId)")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating word usage dates", e)
        }
    }

    // Убираем только звездочки, НО СОХРАНЯЕМ разделители предложений
    private fun cleanTextForDisplay(text: String): String {
        return text
            .replace(Regex("""\*([^*]+)\*""")) { matchResult ->
                matchResult.groupValues[1] // Возвращаем только текст внутри звездочек
            }
    }

    // Удаляем разделители только при отображении в UI
    private fun cleanTextForUI(text: String): String {
        return text.replace(SENTENCE_SEPARATOR, "")
    }

    private fun cleanTextForSpeech(text: String): String {
        // Убираем звездочки и другие специальные символы, оставляем только текст и базовую пунктуацию
        return text
            .replace(Regex("""\*([^*]+)\*""")) { matchResult ->
                matchResult.groupValues[1]
            }
            .replace(Regex("[^\\p{L}\\p{N}\\s.,!?;:-]"), "") // Оставляем буквы, цифры, пробелы и основные знаки пунктуации
    }

    // Разбить текст на предложения по специальным разделителям
    private fun splitIntoSentences(text: String): List<String> {
        Log.d(TAG, "Original text contains ${text.count { it == '.' }} periods")
        Log.d(TAG, "Original text contains ${text.count { it == '!' }} exclamation marks")
        Log.d(TAG, "Original text contains ${text.count { it == '?' }} question marks")
        Log.d(TAG, "Original text contains ${text.split(SENTENCE_SEPARATOR).size - 1} separators")

        val sentences = text.split(SENTENCE_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        Log.d(TAG, "Split text into ${sentences.size} sentences using separators")
        sentences.forEachIndexed { index, sentence ->
            if (index < 3) { // Логируем только первые несколько предложений для экономии места в логах
                Log.d(TAG, "Sentence ${index + 1}: first 50 chars: ${sentence.take(50)}...")
            }
        }

        // Если разделители не сработали (получилось только 1 предложение),
        // логируем подробную информацию для отладки
        if (sentences.size <= 1 && text.length > 100) {
            Log.w(TAG, "Failed to split by separators, text too long for single sentence")
            // Только для логирования, не возвращаем этот результат
            val fallbackSentences = text.split(Regex("(?<=[.!?])\\s+(?=[A-ZА-Я])"))
            Log.d(TAG, "Fallback splitting would yield ${fallbackSentences.size} sentences")

            // Логируем первые и последние 100 символов для анализа
            val start = text.take(100)
            val end = if (text.length > 100) text.takeLast(100) else ""
            Log.d(TAG, "Text start: $start")
            Log.d(TAG, "Text end: $end")
        }

        return sentences
    }

    private fun setTTSLanguage(isRussian: Boolean) {
        tts?.let { tts ->
            val locale = if (isRussian) {
                Locale("ru")
            } else {
                Locale.US
            }
            val result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language ${locale.language} is not supported")
                _uiState.value = UiState.Error("Language ${if (isRussian) "Russian" else "English"} is not supported on this device")
            }
        }
    }

    fun speakText(context: Context, text: String, highlightedSentence: String = "") {
        try {
            initializeTTS(context)

            if (isSpeaking) {
                stopSpeaking()
                return
            }

            val cleanedText = cleanTextForSpeech(text)
            if (cleanedText.isBlank()) {
                return
            }

            // Устанавливаем режим чтения полного текста
            isReadingWords = false

            // Устанавливаем язык в зависимости от текущего состояния
            (_uiState.value as? UiState.Success)?.let { state ->
                setTTSLanguage(state.isRussian)
            }

            // Разбиваем текст на предложения по разделителям
            sentences = splitIntoSentences(text)

            if (sentences.isNotEmpty()) {
                isSpeaking = true

                // Определяем начальный индекс
                currentSentenceIndex = if (highlightedSentence.isNotEmpty()) {
                    sentences.indexOfFirst { it.trim() == highlightedSentence.trim() }.takeIf { it >= 0 } ?: 0
                } else {
                    0
                }

                // Обновляем UI состояние
                viewModelScope.launch {
                    _uiState.value = (_uiState.value as? UiState.Success)?.copy(
                        isSpeaking = true,
                        lastHighlightedSentence = ""  // Сбрасываем последнее подсвеченное предложение
                    ) as UiState ?: _uiState.value
                }

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

                Log.d(TAG, "Speaking sentence ${currentSentenceIndex + 1}/${sentences.size}: \"$sentence\"")

                // Обновляем UI с текущим предложением
                viewModelScope.launch {
                    _uiState.value = (_uiState.value as? UiState.Success)?.copy(
                        currentSpokenWord = sentence
                    ) as UiState ?: _uiState.value
                }

                tts?.speak(cleanTextForSpeech(sentence), TextToSpeech.QUEUE_FLUSH, null, "sentence_$currentSentenceIndex")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking sentence", e)
            _uiState.value = UiState.Error("Error speaking sentence: ${e.localizedMessage}")
        }
    }

    fun speakWord(context: Context, word: String) {
        initializeTTS(context)
        stopSpeaking()
        // Устанавливаем язык в зависимости от текущего состояния
        (_uiState.value as? UiState.Success)?.let { state ->
            setTTSLanguage(state.isRussian)
        }
        tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "single_word")
    }

    fun stopSpeaking() {
        try {
            if (isReadingWords) {
                // Сохраняем индекс последнего слова
                viewModelScope.launch {
                    _uiState.value = (_uiState.value as? UiState.Success)?.copy(
                        currentSpokenWord = "",
                        isSpeaking = false,
                        lastSpokenWordIndex = currentWordIndex
                    ) as UiState ?: _uiState.value
                }
            } else {
                // Сохраняем текущее предложение
                if (currentSentenceIndex < sentences.size) {
                    lastHighlightedSentence = sentences[currentSentenceIndex]

                    // Обновляем UI, сохраняя подсветку при остановке
                    viewModelScope.launch {
                        _uiState.value = (_uiState.value as? UiState.Success)?.copy(
                            // Оставляем текущее прочитанное предложение подсвеченным при остановке
                            lastHighlightedSentence = lastHighlightedSentence,
                            isSpeaking = false
                        ) as UiState ?: _uiState.value
                    }
                }
            }

            tts?.stop()
            isSpeaking = false
            isReadingWords = false
            currentSentenceIndex = 0
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

            // Устанавливаем режим чтения полного текста
            isReadingWords = false
            isSpeaking = true

            // Обновляем UI состояние
            viewModelScope.launch {
                _uiState.value = (_uiState.value as? UiState.Success)?.copy(
                    isSpeaking = true
                ) as UiState ?: _uiState.value
            }

            tts?.speak(cleanTextForSpeech(text), TextToSpeech.QUEUE_FLUSH, null, "smooth_reading")
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

                // Сохраняем оригинальный текст с разделителями для разбиения,
                // но для отображения пользователю удаляем разделители
                val originalText = result.first
                val displayText = cleanTextForUI(cleanTextForDisplay(originalText))

                _uiState.value = UiState.Success(
                    englishVersion = originalText, // Сохраняем оригинальный текст с разделителями
                    englishDisplayVersion = displayText, // Версия для отображения (без разделителей)
                    russianVersion = "",
                    russianDisplayVersion = "",
                    selectedWords = result.third,
                    generationTime = generationTime,
                    isSpeaking = false,
                    lastHighlightedSentence = "",
                    lastSpokenWordIndex = 0
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    fun speakTextWithHighlight(context: Context, text: String, highlightedSentence: String = "") {
        try {
            initializeTTS(context)

            if (isSpeaking) {
                stopSpeaking()
                return
            }

            val cleanedText = cleanTextForSpeech(text)
            if (cleanedText.isBlank()) {
                return
            }

            // Устанавливаем режим чтения полного текста
            isReadingWords = false

            // Устанавливаем язык в зависимости от текущего состояния
            (_uiState.value as? UiState.Success)?.let { state ->
                setTTSLanguage(state.isRussian)
            }

            // Разбиваем текст на предложения по разделителям
            sentences = splitIntoSentences(text)

            if (sentences.isNotEmpty()) {
                isSpeaking = true

// Определяем начальный индекс:
                // 1. Если пользователь выбрал предложение, начинаем с него
                // 2. Если нет выбранного предложения, но есть последнее подсвеченное - начинаем с него
                // 3. Иначе начинаем с начала
                currentSentenceIndex = when {
                    highlightedSentence.isNotEmpty() -> {
                        val index = sentences.indexOfFirst { sent ->
                            sent.replace(SENTENCE_SEPARATOR, "").trim() == highlightedSentence.replace(SENTENCE_SEPARATOR, "").trim()
                        }
                        if (index >= 0) index else 0
                    }
                    lastHighlightedSentence.isNotEmpty() -> {
                        val index = sentences.indexOfFirst { sent ->
                            sent.replace(SENTENCE_SEPARATOR, "").trim() == lastHighlightedSentence.replace(SENTENCE_SEPARATOR, "").trim()
                        }
                        if (index >= 0) index else 0
                    }
                    else -> 0
                }

                // Обновляем UI с первым предложением и статусом воспроизведения
                viewModelScope.launch {
                    _uiState.value = (_uiState.value as? UiState.Success)?.copy(
                        currentSpokenWord = sentences[currentSentenceIndex],
                        lastHighlightedSentence = "", // Сбрасываем сохраненное предложение при начале чтения
                        isSpeaking = true
                    ) as UiState ?: _uiState.value
                }

                speakNextSentence()
            }
        } catch (e: Exception) {
            _uiState.value = UiState.Error("Error starting speech: ${e.localizedMessage}")
        }
    }

    // Новая функция для воспроизведения выбранных слов
    fun speakSelectedWords(context: Context, words: List<String>) {
        initializeTTS(context)

        if (isSpeaking) {
            stopSpeaking()
            return
        }

        if (words.isEmpty()) return

        // Получаем текущее состояние
        val currentState = _uiState.value as? UiState.Success ?: return

        // Устанавливаем режим чтения отдельных слов
        isReadingWords = true

        // Устанавливаем язык
        setTTSLanguage(false) // Всегда используем английский для слов

        // Сохраняем список слов
        selectedWords = words

        // Начинаем с последнего произнесенного слова или с начала
        currentWordIndex = currentState.lastSpokenWordIndex

        // Проверяем, что индекс находится в пределах списка
        if (currentWordIndex >= words.size) {
            currentWordIndex = 0
        }

        // Обновляем состояние UI
        viewModelScope.launch {
            _uiState.value = currentState.copy(
                isSpeaking = true,
                currentSpokenWord = words[currentWordIndex]
            ) as UiState
        }

        // Запускаем корутину для последовательного воспроизведения
        viewModelScope.launch {
            try {
                isSpeaking = true

                while (isSpeaking && isReadingWords) {
                    if (currentWordIndex >= words.size) {
                        currentWordIndex = 0  // Начинаем сначала, если дошли до конца списка
                    }

                    val word = words[currentWordIndex]

                    // Обновляем текущее слово в UI
                    _uiState.value = (_uiState.value as? UiState.Success)?.copy(
                        currentSpokenWord = word
                    ) as UiState ?: _uiState.value

                    // Произносим слово
                    val latch = CountDownLatch(1)
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String) {}

                        override fun onDone(utteranceId: String) {
                            if (utteranceId.startsWith("word_")) {
                                latch.countDown()
                            }
                        }

                        override fun onError(utteranceId: String) {
                            if (utteranceId.startsWith("word_")) {
                                latch.countDown()
                            }
                        }
                    })

                    tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "word_${System.currentTimeMillis()}")

                    // Ждем окончания произношения и делаем паузу в 1 секунду
                    latch.await(5, TimeUnit.SECONDS)  // Таймаут 5 секунд на всякий случай

                    // Пауза между словами только если все еще в режиме чтения слов
                    if (isSpeaking && isReadingWords) {
                        delay(1000)  // Пауза между словами
                        currentWordIndex++  // Переходим к следующему слову
                    } else {
                        break  // Выходим из цикла, если чтение остановлено
                    }
                }

                // По окончании цикла обновляем UI
                if (!isSpeaking || !isReadingWords) {
                    _uiState.value = (_uiState.value as? UiState.Success)?.copy(
                        currentSpokenWord = "",
                        isSpeaking = false,
                        lastSpokenWordIndex = currentWordIndex
                    ) as UiState ?: _uiState.value
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error while speaking words: ${e.message}")
                isReadingWords = false
                stopSpeaking()
            }
        }
    }

    fun toggleLanguage() {
        val currentState = _uiState.value
        if (currentState is UiState.Success) {
            _uiState.value = currentState.copy(isTranslating = true) as UiState
            viewModelScope.launch {
                // Если переключаемся на русский и перевода еще нет
                if (!currentState.isRussian && currentState.russianVersion.isEmpty()) {
                    try {
                        val translationPrompt = """
                            Translate this story to Russian. Keep the same formatting and paragraph breaks.
                            Mark the translated equivalents of marked words with asterisks.
                            
                            CRITICAL INSTRUCTION: After each complete sentence (ending with period, exclamation mark, or question mark), insert the exact text "$SENTENCE_SEPARATOR". 
                            DO NOT abbreviate or modify this separator in any way. 
                            DO NOT skip adding this separator after ANY sentence.
                            Include this separator even at paragraph breaks.
                            
                            Example format:
                            This is the first sentence.$SENTENCE_SEPARATOR This is the second sentence.$SENTENCE_SEPARATOR
                            
                            Story to translate:
                            ${currentState.englishVersion}
                            
                            Provide only the Russian translation, no additional text.
                        """.trimIndent()

                        Log.d(TAG, "Requesting translation")
                        val response = generativeModel.generateContent(translationPrompt)
                        var russianStory = response.text?.trim() ?: throw Exception("Empty translation response")

                        Log.d(TAG, "Russian translation raw first 500 chars: ${russianStory.take(500)}")
                        Log.d(TAG, "Russian contains separators: ${russianStory.contains(SENTENCE_SEPARATOR)}")
                        Log.d(TAG, "Russian count of separators: ${russianStory.split(SENTENCE_SEPARATOR).size - 1}")

                        // Очищаем текст от звездочек, но сохраняем специальные разделители предложений
                        russianStory = russianStory.replace(Regex("""\*([^*]+)\*""")) { matchResult ->
                            matchResult.groupValues[1] // Возвращаем только текст внутри звездочек
                        }

                        // Создаем версию для отображения
                        val russianDisplayText = cleanTextForUI(russianStory)

                        Log.d(TAG, "Translation received")
                        Log.d(TAG, "Russian story length: ${russianStory.length}")

                        _uiState.value = currentState.copy(
                            russianVersion = russianStory, // С разделителями
                            russianDisplayVersion = russianDisplayText, // Без разделителей
                            isRussian = true,
                            isTranslating = false
                        ) as UiState
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
                    ) as UiState
                }
            }
        }
    }

    override fun onCleared() {
        try {
            isSmoothReading = false
            isReadingWords = false
            stopSpeaking()
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            // Игнорируем ошибки при закрытии
        }
        super.onCleared()
    }
}