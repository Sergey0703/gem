// app/src/main/java/com/example/gem/BakingScreen.kt

package com.example.gem

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gem.ui.components.EmptyStateCard
import com.example.gem.ui.components.ErrorCard
import com.example.gem.ui.components.LoadingOverlay
import com.example.gem.ui.components.StoryContent
import com.example.gem.ui.components.StoryControls
import com.example.gem.ui.components.StoryHeader
import com.example.gem.ui.components.StoryWordInfoDialog
import com.example.gem.ui.components.handleWordLongPress

@Composable
fun BakingScreen(
    storyViewModel: StoryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by storyViewModel.uiState.collectAsState()

    // Special sentence separator
    val sentenceSeparator = "<<SENTENCE_END>>"

    var selectedWord by remember { mutableStateOf<String?>(null) }
    var showWordDialog by remember { mutableStateOf(false) }
    var showSelectedWords by remember { mutableStateOf(false) }
    var wordInfo by remember { mutableStateOf(Triple("", "", "")) }
    var highlightedSentence by remember { mutableStateOf("") }
    var highlightedSentenceIndex by remember { mutableStateOf(-1) }
    var speechRate by remember { mutableStateOf(1.0f) }
    var isGenerating by remember { mutableStateOf(false) }

    // Create scroll state
    val scrollState = rememberScrollState()

    // Track generation state
    LaunchedEffect(uiState) {
        isGenerating = uiState is UiState.Loading
    }

    // Initialize TextToSpeech
    LaunchedEffect(Unit) {
        storyViewModel.initializeTTS(context)
    }

    // Display word info dialog
    StoryWordInfoDialog(
        show = showWordDialog,
        selectedWord = selectedWord,
        wordInfo = wordInfo,
        onDismiss = {
            showWordDialog = false
            selectedWord = null
        },
        onPlayClick = { word ->
            storyViewModel.speakWord(context, word)
        }
    )

    // If generating, show progress overlay
    if (isGenerating && uiState is UiState.Loading) {
        LoadingOverlay(uiState as UiState.Loading)
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // App title
        Text(
            text = stringResource(R.string.app_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        when (val state = uiState) {
            is UiState.Loading -> {
                // Loading content is displayed via LoadingOverlay
            }
            is UiState.Success -> {
                // Header with text information
                StoryHeader(
                    state = state,
                    onGenerateClick = { storyViewModel.startStoryGeneration("") },
                    onLanguageToggleClick = { storyViewModel.toggleLanguage() }
                )

                // Main content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    // Split text into sentences
                    val textWithSeparators = if (state.isRussian) state.russianVersion else state.englishVersion
                    val sentences = textWithSeparators.split(sentenceSeparator)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .map { it + sentenceSeparator } // Add separator back for correct comparison

                    // Find current sentence index based on currentSpokenWord
                    val currentSentenceIndex = sentences.indexOfFirst { sentence ->
                        val cleanSentence = sentence.replace(sentenceSeparator, "").trim()
                        val stateSentence = state.currentSpokenWord.replace(sentenceSeparator, "").trim()
                        cleanSentence == stateSentence
                    }

                    // Current position for display in controls (1-based)
                    val displayCurrentIndex = if (currentSentenceIndex >= 0) currentSentenceIndex + 1 else 0
                    val totalSentences = sentences.size

                    // Build title for control panel - ALWAYS IN ENGLISH
                    val title = if (showSelectedWords) {
                        "Selected words"
                    } else {
                        "Story"
                    }

                    // Additional subtitle info
                    val subtitleInfo = if (!showSelectedWords && displayCurrentIndex > 0 && totalSentences > 0) {
                        "$displayCurrentIndex/$totalSentences"
                    } else ""

                    // Story content
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp)
                        ) {
                            // Controls for story
                            StoryControls(
                                title = title,
                                subtitleInfo = subtitleInfo,
                                showSelectedWords = showSelectedWords,
                                isSpeaking = state.isSpeaking,
                                speechRate = speechRate,
                                onToggleViewClick = { showSelectedWords = !showSelectedWords },
                                onSpeechRateChange = { newRate ->
                                    speechRate = newRate
                                    storyViewModel.setSpeechRate(newRate)
                                },
                                onPlayStopClick = {
                                    if (state.isSpeaking) {
                                        storyViewModel.stopSpeaking()
                                    } else {
                                        if (showSelectedWords) {
                                            storyViewModel.speakSelectedWords(context, state.selectedWords)
                                        } else {
                                            storyViewModel.speakTextWithHighlight(
                                                context,
                                                if (state.isRussian) state.russianVersion else state.englishVersion,
                                                highlightedSentence,
                                                highlightedSentenceIndex
                                            )
                                        }
                                    }
                                }
                            )

                            // Main content - text or word list
                            StoryContent(
                                showSelectedWords = showSelectedWords,
                                sentences = sentences,
                                textForDisplay = if (state.isRussian) state.russianDisplayVersion else state.englishDisplayVersion,
                                currentSpokenWord = state.currentSpokenWord,
                                isSpeaking = state.isSpeaking,
                                highlightedSentence = highlightedSentence,
                                lastHighlightedSentence = state.lastHighlightedSentence,
                                scrollState = scrollState,
                                onSentenceClick = { sentence, index ->
                                    // For debugging
                                    Log.d("BakingScreen", "Clicked sentence $index: ${sentence.take(30)}...")

                                    // Always save index when selecting a sentence
                                    highlightedSentenceIndex = if (highlightedSentence == sentence) -1 else index
                                    highlightedSentence = if (highlightedSentence == sentence) "" else sentence

                                    Log.d("BakingScreen", "After click, index=$highlightedSentenceIndex, sentence=${highlightedSentence.take(30)}")
                                },
                                onWordLongPress = handleWordLongPress(
                                    storyViewModel = storyViewModel,
                                    onSelectedWordInfo = { word, info ->
                                        selectedWord = word
                                        wordInfo = info
                                        showWordDialog = true
                                    }
                                ),
                                selectedWords = state.selectedWords,
                                onWordClick = { word ->
                                    selectedWord = word
                                    storyViewModel.getWordInfoAndUpdate(word) { info ->
                                        wordInfo = info
                                        showWordDialog = true
                                    }
                                },
                                // Added parameters for direct index-based highlighting
                                highlightedSentenceIndex = highlightedSentenceIndex,
                                currentSentenceIndex = currentSentenceIndex,
                                lastHighlightedSentenceIndex = state.lastHighlightedSentenceIndex
                            )
                        }
                    }
                }
            }
            is UiState.Error -> {
                ErrorCard(
                    errorMessage = state.message,
                    onRetryClick = { storyViewModel.startStoryGeneration("") }
                )
            }
            else -> {
                EmptyStateCard(
                    onGenerateClick = { storyViewModel.startStoryGeneration("") }
                )
            }
        }
    }
}