// app/src/main/java/com/example/gem/domain/TextToSpeechUseCase.kt

package com.example.gem.domain

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.gem.UiState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TextToSpeechUseCase @Inject constructor() {
    private val TAG = "TextToSpeechUseCase"
    private var stateUpdateCallback: ((UiState) -> UiState) -> Unit = {}

    private var tts: TextToSpeech? = null
    private var isSpeaking = false
    private var isRussian = false
    private var currentSentenceIndex = 0
    private var sentences = listOf<String>()
    private var speechRate = 1.0f
    private var currentLanguage = "en"
    private var isSmoothReading = false

    // Flag to track reading mode
    private var isReadingWords = false
    private var lastModeWasReadingWords = false

    // For word reading mode
    private var currentWordIndex = 0
    private var lastSpokenWordIndex = 0
    private var selectedWords = listOf<String>()

    // Variable to store last read sentence
    private var lastHighlightedSentence = ""
    private var lastHighlightedSentenceIndex = -1

    // TTS initialization state
    private var isTtsInitialized = false
    private var ttsInitializationInProgress = false

    // Special sentence separator
    private val SENTENCE_SEPARATOR = "<<SENTENCE_END>>"

    // Error handler for coroutines
    private val errorHandler = CoroutineExceptionHandler { _, exception ->
        Log.e(TAG, "Coroutine exception: ${exception.message}", exception)
    }

    // Coroutine scope for this use case
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob() + errorHandler)

    fun setStateUpdateCallback(callback: ((UiState) -> UiState) -> Unit) {
        stateUpdateCallback = callback
    }

    fun setSpeechRate(rate: Float) {
        speechRate = rate
        tts?.setSpeechRate(rate)
    }

    // Completely reset TTS and recreate it
    private fun resetTTS(context: Context) {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isTtsInitialized = false
            initialize(context, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting TTS: ${e.message}")
        }
    }

    // Set event handler for text reading
    private fun setTextReadingListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                scope.launch(Dispatchers.Main + SupervisorJob()) {
                    if (utteranceId.startsWith("sentence_") && currentSentenceIndex < sentences.size) {
                        updateState { currentState ->
                            if (currentState is UiState.Success) {
                                currentState.copy(
                                    currentSpokenWord = sentences[currentSentenceIndex],
                                    isSpeaking = true
                                )
                            } else currentState
                        }
                    }
                }
            }

            override fun onDone(utteranceId: String) {
                if (isReadingWords) {
                    // For word reading mode, separate logic is in speakSelectedWords
                    return
                } else if (utteranceId.startsWith("sentence_") && isSpeaking && currentSentenceIndex < sentences.size - 1) {
                    // Logic for sequential sentence reading
                    currentSentenceIndex++
                    speakNextSentence()
                } else if (utteranceId.startsWith("sentence_")) {
                    // Save last spoken sentence
                    if (currentSentenceIndex < sentences.size) {
                        lastHighlightedSentence = sentences[currentSentenceIndex]
                        lastHighlightedSentenceIndex = currentSentenceIndex
                    }

                    isSpeaking = false
                    currentSentenceIndex = 0
                    scope.launch(Dispatchers.Main + SupervisorJob()) {
                        updateState { currentState ->
                            if (currentState is UiState.Success) {
                                currentState.copy(
                                    currentSpokenWord = "",
                                    lastHighlightedSentence = lastHighlightedSentence,
                                    lastHighlightedSentenceIndex = lastHighlightedSentenceIndex, // Added this field
                                    isSpeaking = false
                                )
                            } else currentState
                        }
                    }
                }
            }

            override fun onError(utteranceId: String) {
                isSpeaking = false
                currentSentenceIndex = 0
                scope.launch(Dispatchers.Main + SupervisorJob()) {
                    updateState { currentState ->
                        if (currentState is UiState.Success) {
                            currentState.copy(
                                currentSpokenWord = "",
                                isSpeaking = false
                            )
                        } else currentState
                    }
                }
            }
        })
    }

    fun initialize(context: Context, currentState: UiState?) {
        // Prevent simultaneous initialization
        if (ttsInitializationInProgress) {
            return
        }

        // If TTS is already initialized, do nothing
        if (isTtsInitialized && tts != null) {
            return
        }

        ttsInitializationInProgress = true

        scope.launch(Dispatchers.IO + SupervisorJob() + errorHandler) {
            try {
                // Use CountDownLatch to wait for TTS initialization
                val initLatch = CountDownLatch(1)
                var initSuccess = false

                withContext(Dispatchers.Main) {
                    tts = TextToSpeech(context) { status ->
                        initSuccess = status == TextToSpeech.SUCCESS
                        if (initSuccess) {
                            tts?.language = Locale.US
                            tts?.setSpeechRate(speechRate)
                            setTextReadingListener()
                            isTtsInitialized = true
                        } else {
                            Log.e(TAG, "TTS initialization failed with status $status")
                        }
                        initLatch.countDown()
                    }
                }

                // Wait for initialization with timeout
                val initialized = withContext(Dispatchers.IO) {
                    initLatch.await(5, TimeUnit.SECONDS)
                    initSuccess
                }

                ttsInitializationInProgress = false

                if (!initialized) {
                    withContext(Dispatchers.Main) {
                        updateState { _ -> UiState.Error("Failed to initialize text-to-speech within timeout") }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing TTS: ${e.message}", e)
                ttsInitializationInProgress = false
                withContext(Dispatchers.Main) {
                    updateState { _ -> UiState.Error("Error initializing text-to-speech: ${e.localizedMessage}") }
                }
            }
        }
    }

    private fun setTTSLanguage(isRussian: Boolean) {
        try {
            tts?.let { tts ->
                val locale = if (isRussian) {
                    Locale("ru")
                } else {
                    Locale.US
                }
                val result = tts.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language ${locale.language} is not supported")
                    scope.launch(Dispatchers.Main) {
                        updateState { _ -> UiState.Error("Language ${if (isRussian) "Russian" else "English"} is not supported on this device") }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting TTS language: ${e.message}")
            // Don't show error to user, just log it
        }
    }

    fun speakText(
        context: Context,
        text: String,
        highlightedSentence: String = "",
        currentState: UiState?
    ) {
        try {
            // Check if last mode was word reading
            if (lastModeWasReadingWords) {
                resetTTS(context)
                lastModeWasReadingWords = false
            } else {
                initialize(context, currentState)
            }

            if (isSpeaking) {
                stopSpeaking(currentState)
                return
            }

            val cleanedText = cleanTextForSpeech(text)
            if (cleanedText.isBlank()) {
                return
            }

            // Set mode to full text reading
            isReadingWords = false

            // Check TTS initialization
            if (!isTtsInitialized) {
                scope.launch(Dispatchers.Main + SupervisorJob() + errorHandler) {
                    for (i in 1..5) { // Try 5 times
                        delay(500) // Wait for initialization
                        if (isTtsInitialized) break
                    }

                    if (!isTtsInitialized) {
                        updateState { _ -> UiState.Error("Text-to-speech is not initialized. Please try again.") }
                        return@launch
                    }

                    continueSpeakText(context, text, highlightedSentence, currentState)
                }
            } else {
                continueSpeakText(context, text, highlightedSentence, currentState)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in speakText: ${e.message}", e)
            updateState { _ -> UiState.Error("Error starting speech: ${e.localizedMessage}") }
        }
    }

    private fun continueSpeakText(
        context: Context,
        text: String,
        highlightedSentence: String = "",
        currentState: UiState?
    ) {
        try {
            // Set text reading listener
            setTextReadingListener()

            // Set language based on current state
            if (currentState is UiState.Success) {
                setTTSLanguage(currentState.isRussian)
            }

            // Split text into sentences by separators
            sentences = splitIntoSentences(text)

            if (sentences.isNotEmpty()) {
                isSpeaking = true

                // Determine starting index
                currentSentenceIndex = if (highlightedSentence.isNotEmpty()) {
                    sentences.indexOfFirst { it.trim() == highlightedSentence.trim() }
                        .takeIf { it >= 0 } ?: 0
                } else {
                    0
                }

                // Update UI state
                scope.launch(Dispatchers.Main + SupervisorJob() + errorHandler) {
                    updateState { current ->
                        if (current is UiState.Success) {
                            current.copy(
                                isSpeaking = true,
                                lastHighlightedSentence = "",  // Reset last highlighted sentence
                                lastHighlightedSentenceIndex = -1 // Reset last highlighted sentence index
                            )
                        } else current
                    }
                }

                speakNextSentence()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in continueSpeakText: ${e.message}", e)
            updateState { _ -> UiState.Error("Error continuing speech: ${e.localizedMessage}") }
        }
    }

    private fun speakNextSentence() {
        try {
            if (currentSentenceIndex < sentences.size) {
                val sentence = sentences[currentSentenceIndex]

                Log.d(
                    TAG,
                    "Speaking sentence ${currentSentenceIndex + 1}/${sentences.size}: \"$sentence\""
                )

                // Update UI with current sentence
                scope.launch(Dispatchers.Main + SupervisorJob() + errorHandler) {
                    updateState { currentState ->
                        if (currentState is UiState.Success) {
                            currentState.copy(
                                currentSpokenWord = sentence
                            )
                        } else currentState
                    }
                }

                // Check in case TTS was reset
                if (tts == null || !isTtsInitialized) {
                    Log.e(TAG, "TTS is null or not initialized when trying to speak")
                    isSpeaking = false
                    return
                }

                tts?.speak(
                    cleanTextForSpeech(sentence),
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "sentence_$currentSentenceIndex"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking sentence", e)
            isSpeaking = false
            scope.launch(Dispatchers.Main) {
                updateState { _ -> UiState.Error("Error speaking sentence: ${e.localizedMessage}") }
            }
        }
    }

    fun speakWord(context: Context, word: String, currentState: UiState?) {
        scope.launch(Dispatchers.Main + SupervisorJob() + errorHandler) {
            initialize(context, currentState)

            // Wait for TTS initialization
            for (i in 1..5) {
                if (isTtsInitialized) break
                delay(200)
            }

            if (!isTtsInitialized) {
                updateState { _ -> UiState.Error("Could not initialize text-to-speech") }
                return@launch
            }

            stopSpeaking(currentState)

            // Set language based on current state
            if (currentState is UiState.Success) {
                setTTSLanguage(currentState.isRussian)
            }

            tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "single_word")
        }
    }

    fun stopSpeaking(currentState: UiState?) {
        try {
            if (isReadingWords) {
                // Save index of last word
                scope.launch(Dispatchers.Main + SupervisorJob() + errorHandler) {
                    updateState { state ->
                        if (state is UiState.Success) {
                            state.copy(
                                currentSpokenWord = "",
                                isSpeaking = false,
                                lastSpokenWordIndex = currentWordIndex
                            )
                        } else state
                    }
                }
                lastModeWasReadingWords = true
            } else {
                // Save current sentence
                if (currentSentenceIndex < sentences.size) {
                    lastHighlightedSentence = sentences[currentSentenceIndex]
                    lastHighlightedSentenceIndex = currentSentenceIndex

                    // Update UI, keeping highlight on stop
                    scope.launch(Dispatchers.Main + SupervisorJob() + errorHandler) {
                        updateState { state ->
                            if (state is UiState.Success) {
                                state.copy(
                                    // Keep current read sentence highlighted when stopped
                                    lastHighlightedSentence = lastHighlightedSentence,
                                    lastHighlightedSentenceIndex = lastHighlightedSentenceIndex, // Added this
                                    isSpeaking = false
                                )
                            } else state
                        }
                    }
                }
                lastModeWasReadingWords = false
            }

            tts?.stop()
            isSpeaking = false
            isReadingWords = false
            currentSentenceIndex = 0
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech: ${e.message}", e)
            // Don't show error to user, just stop playback
            isSpeaking = false
            isReadingWords = false
        }
    }

    fun speakTextWithHighlight(
        context: Context,
        text: String,
        highlightedSentence: String = "",
        sentenceIndex: Int = -1,
        currentState: UiState?
    ) {
        scope.launch(Dispatchers.Main + SupervisorJob() + errorHandler) {
            try {
                // Check if last mode was word reading
                if (lastModeWasReadingWords) {
                    resetTTS(context)
                    lastModeWasReadingWords = false
                } else {
                    initialize(context, currentState)
                }

                if (isSpeaking) {
                    stopSpeaking(currentState)
                    return@launch
                }

                val cleanedText = cleanTextForSpeech(text)
                if (cleanedText.isBlank()) {
                    return@launch
                }

                // Wait for TTS initialization
                for (i in 1..5) {
                    if (isTtsInitialized) break
                    delay(200)
                }

                if (!isTtsInitialized) {
                    updateState { _ -> UiState.Error("Could not initialize text-to-speech") }
                    return@launch
                }

                // Set mode to full text reading
                isReadingWords = false

                // Set text reading listener
                setTextReadingListener()

                // Set language based on current state
                if (currentState is UiState.Success) {
                    setTTSLanguage(currentState.isRussian)
                }

                // Split text into sentences by separators
                sentences = splitIntoSentences(text)

                if (sentences.isNotEmpty()) {
                    isSpeaking = true

                    // Determine starting index with priority to the direct sentence index
                    currentSentenceIndex = when {
                        sentenceIndex >= 0 && sentenceIndex < sentences.size -> {
                            // First priority: direct index if valid
                            Log.d(TAG, "Using direct index: $sentenceIndex")
                            sentenceIndex
                        }

                        highlightedSentence.isNotEmpty() -> {
                            // Second priority: find by sentence content
                            val index = sentences.indexOfFirst { sent ->
                                sent.replace(SENTENCE_SEPARATOR, "").trim() ==
                                        highlightedSentence.replace(SENTENCE_SEPARATOR, "").trim()
                            }
                            if (index >= 0) {
                                Log.d(TAG, "Found sentence by content at index: $index")
                                index
                            } else 0
                        }

                        currentState is UiState.Success &&
                                currentState.lastHighlightedSentenceIndex >= 0 &&
                                currentState.lastHighlightedSentenceIndex < sentences.size -> {
                            // Third priority: use last highlighted index from state
                            Log.d(
                                TAG,
                                "Using last highlighted index from state: ${currentState.lastHighlightedSentenceIndex}"
                            )
                            currentState.lastHighlightedSentenceIndex
                        }

                        lastHighlightedSentenceIndex >= 0 && lastHighlightedSentenceIndex < sentences.size -> {
                            // Fourth priority: use last highlighted index from use case
                            Log.d(
                                TAG,
                                "Using last highlighted index from use case: $lastHighlightedSentenceIndex"
                            )
                            lastHighlightedSentenceIndex
                        }

                        lastHighlightedSentence.isNotEmpty() -> {
                            // Fifth priority: find last highlighted sentence by content
                            val index = sentences.indexOfFirst { sent ->
                                sent.replace(SENTENCE_SEPARATOR, "").trim() ==
                                        lastHighlightedSentence.replace(SENTENCE_SEPARATOR, "")
                                            .trim()
                            }
                            if (index >= 0) {
                                Log.d(TAG, "Found last highlighted by content at index: $index")
                                index
                            } else 0
                        }

                        else -> 0
                    }

                    // Log the chosen starting sentence
                    Log.d(TAG, "Starting speech at sentence index: $currentSentenceIndex")
                    if (currentSentenceIndex < sentences.size) {
                        Log.d(
                            TAG,
                            "Starting sentence: ${sentences[currentSentenceIndex].take(30)}..."
                        )
                    }

                    // Update UI with first sentence and playback status
                    updateState { current ->
                        if (current is UiState.Success) {
                            current.copy(
                                currentSpokenWord = sentences[currentSentenceIndex],
                                lastHighlightedSentence = "", // Reset saved sentence when starting reading
                                lastHighlightedSentenceIndex = -1, // Reset saved index when starting reading
                                isSpeaking = true
                            )
                        } else current
                    }

                    speakNextSentence()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in speakTextWithHighlight: ${e.message}", e)
                updateState { _ -> UiState.Error("Error starting speech: ${e.localizedMessage}") }
            }
        }
    }

    fun speakSelectedWords(context: Context, words: List<String>, currentState: UiState?) {
        scope.launch(Dispatchers.Main + SupervisorJob() + errorHandler) {
            try {
                // Completely reset TTS when switching to word reading mode
                resetTTS(context)

                if (isSpeaking) {
                    stopSpeaking(currentState)
                    return@launch
                }

                if (words.isEmpty()) return@launch

                // Wait for TTS initialization
                for (i in 1..5) {
                    if (isTtsInitialized) break
                    delay(200)
                }

                if (!isTtsInitialized) {
                    updateState { _ -> UiState.Error("Could not initialize text-to-speech") }
                    return@launch
                }

                // Get current state
                val state = currentState as? UiState.Success ?: return@launch

                // Set word reading mode
                isReadingWords = true
                lastModeWasReadingWords = true

                // Set language
                setTTSLanguage(false) // Always use English for words

                // Save word list
                selectedWords = words

                // Start from last spoken word or beginning
                currentWordIndex = state.lastSpokenWordIndex

                // Check index is within list bounds
                if (currentWordIndex >= words.size) {
                    currentWordIndex = 0
                }

                // Update UI state
                updateState { current ->
                    if (current is UiState.Success) {
                        current.copy(
                            isSpeaking = true,
                            currentSpokenWord = words[currentWordIndex]
                        )
                    } else current
                }

                // Create custom event handler for words
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) {}

                    override fun onDone(utteranceId: String) {
                        if (utteranceId.startsWith("word_")) {
                            // Do nothing - loop control is via coroutine
                        }
                    }

                    override fun onError(utteranceId: String) {
                        if (utteranceId.startsWith("word_")) {
                            // Do nothing - errors handled in coroutine
                        }
                    }
                })

                isSpeaking = true

                // Use withContext to run in background thread
                withContext(Dispatchers.Default + SupervisorJob() + errorHandler) {
                    try {
                        while (isSpeaking && isReadingWords) {
                            if (currentWordIndex >= words.size) {
                                currentWordIndex = 0  // Start over if we reached end of list
                            }

                            val word = words[currentWordIndex]

                            // Update current word in UI
                            withContext(Dispatchers.Main) {
                                updateState { current ->
                                    if (current is UiState.Success) {
                                        current.copy(
                                            currentSpokenWord = word
                                        )
                                    } else current
                                }
                            }

                            // Speak word
                            val latch = CountDownLatch(1)

                            withContext(Dispatchers.Main) {
                                tts?.setOnUtteranceProgressListener(object :
                                    UtteranceProgressListener() {
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

                                tts?.speak(
                                    word,
                                    TextToSpeech.QUEUE_FLUSH,
                                    null,
                                    "word_${System.currentTimeMillis()}"
                                )
                            }

                            // Wait for speech to finish and pause for 1 second
                            withTimeout(5000) { // 5 second timeout
                                latch.await()
                            }

                            // Pause between words only if still in word reading mode
                            if (isSpeaking && isReadingWords) {
                                delay(1000)  // Pause between words
                                currentWordIndex++  // Move to next word
                            } else {
                                break  // Exit loop if reading stopped
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in word reading loop: ${e.message}", e)
                    } finally {
                        // Update UI when loop ends
                        withContext(Dispatchers.Main) {
                            if (!isSpeaking || !isReadingWords) {
                                updateState { current ->
                                    if (current is UiState.Success) {
                                        current.copy(
                                            currentSpokenWord = "",
                                            isSpeaking = false,
                                            lastSpokenWordIndex = currentWordIndex
                                        )
                                    } else current
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in speakSelectedWords: ${e.message}", e)
                updateState { _ -> UiState.Error("Error speaking words: ${e.localizedMessage}") }
            }
        }
    }

    private fun cleanTextForSpeech(text: String): String {
        // Remove asterisks, sentence separators, and other special characters
        // Keep only text and basic punctuation
        return text
            .replace(SENTENCE_SEPARATOR, "") // Remove sentence separators
            .replace(Regex("""\*([^*]+)\*""")) { matchResult ->
                matchResult.groupValues[1] // Return only text inside asterisks
            }
            .replace(
                Regex("[^\\p{L}\\p{N}\\s.,!?;:-]"),
                ""
            ) // Keep letters, numbers, spaces and main punctuation marks
    }

    private fun splitIntoSentences(text: String): List<String> {
        Log.d(TAG, "Original text contains ${text.count { it == '.' }} periods")
        Log.d(TAG, "Original text contains ${text.count { it == '!' }} exclamation marks")
        Log.d(TAG, "Original text contains ${text.count { it == '?' }} question marks")
        Log.d(TAG, "Original text contains ${text.split(SENTENCE_SEPARATOR).size - 1} separators")

        val sentences = text.split(SENTENCE_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it + SENTENCE_SEPARATOR } // Add separator back for correct comparison

        Log.d(TAG, "Split text into ${sentences.size} sentences using separators")
        sentences.forEachIndexed { index, sentence ->
            if (index < 3) { // Log only first few sentences to save space
                Log.d(TAG, "Sentence ${index + 1}: first 50 chars: ${sentence.take(50)}...")
            }
        }

        // If separators didn't work (only 1 sentence), log detailed info for debugging
        if (sentences.size <= 1 && text.length > 100) {
            Log.w(TAG, "Failed to split by separators, text too long for single sentence")
            // For logging only, don't return this result
            val fallbackSentences = text.split(Regex("(?<=[.!?])\\s+(?=[A-ZА-Я])"))
            Log.d(TAG, "Fallback splitting would yield ${fallbackSentences.size} sentences")

            // Log first and last 100 characters for analysis
            val start = text.take(100)
            val end = if (text.length > 100) text.takeLast(100) else ""
            Log.d(TAG, "Text start: $start")
            Log.d(TAG, "Text end: $end")
        }

        return sentences
    }

    private fun updateState(updater: (UiState) -> UiState) {
        stateUpdateCallback(updater)
    }

    fun shutdown() {
        try {
            isSmoothReading = false
            isReadingWords = false
            lastModeWasReadingWords = false
            stopSpeaking(null)
            tts?.shutdown()
            tts = null
            isTtsInitialized = false
        } catch (e: Exception) {
            // Ignore errors on shutdown
            Log.e(TAG, "Error in shutdown: ${e.message}", e)
        }
    }
}