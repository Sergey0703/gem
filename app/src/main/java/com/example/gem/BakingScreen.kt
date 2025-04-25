package com.example.gem

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

    // Специальный разделитель предложений
    val sentenceSeparator = "<<SENTENCE_END>>"

    var selectedWord by remember { mutableStateOf<String?>(null) }
    var showWordDialog by remember { mutableStateOf(false) }
    var showSelectedWords by remember { mutableStateOf(false) }
    var wordInfo by remember { mutableStateOf(Triple("", "", "")) }
    var highlightedSentence by remember { mutableStateOf("") }
    var speechRate by remember { mutableStateOf(1.0f) }
    var isGenerating by remember { mutableStateOf(false) }

    // Создаем состояние скролла
    val scrollState = rememberScrollState()

    // Следим за состоянием генерации
    LaunchedEffect(uiState) {
        isGenerating = uiState is UiState.Loading
    }

    // Инициализация TextToSpeech
    LaunchedEffect(Unit) {
        storyViewModel.initializeTTS(context)
    }

    // Отображаем диалог информации о слове
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

    // Если идет генерация, показываем оверлей с прогрессом
    if (isGenerating && uiState is UiState.Loading) {
        LoadingOverlay(uiState as UiState.Loading)
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Заголовок приложения
        Text(
            text = stringResource(R.string.app_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        when (val state = uiState) {
            is UiState.Loading -> {
                // Контент загрузки отображается через LoadingOverlay
            }
            is UiState.Success -> {
                // Заголовок с информацией о тексте
                StoryHeader(
                    state = state,
                    onGenerateClick = { storyViewModel.startStoryGeneration("") },
                    onLanguageToggleClick = { storyViewModel.toggleLanguage() }
                )

                // Основное содержимое
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    // Разбиваем текст на предложения
                    val textWithSeparators = if (state.isRussian) state.russianVersion else state.englishVersion
                    val sentences = textWithSeparators.split(sentenceSeparator)
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .map { it + sentenceSeparator } // Добавляем разделитель обратно для корректного сравнения

                    // Находим текущее предложение
                    val currentSentenceIndex = sentences.indexOfFirst { sentence ->
                        val cleanSentence = sentence.replace(sentenceSeparator, "").trim()
                        val stateSentence = state.currentSpokenWord.replace(sentenceSeparator, "").trim()
                        cleanSentence == stateSentence
                    }.let { if (it >= 0) it + 1 else 0 }

                    val totalSentences = sentences.size

                    // Построение заголовка для панели управления
                    val title = if (showSelectedWords) {
                        if (!state.isRussian) "Selected words:" else "Выбранные слова:"
                    } else {
                        if (!state.isRussian) "Story:" else "История:"
                    }

                    // Дополнительная информация для подзаголовка
                    val subtitleInfo = if (!showSelectedWords && currentSentenceIndex > 0 && totalSentences > 0) {
                        "$currentSentenceIndex/$totalSentences"
                    } else ""

                    // Содержимое истории
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
                            // Панель управления для истории
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
                                                highlightedSentence
                                            )
                                        }
                                    }
                                }
                            )

                            // Основной контент - текст или список слов
                            StoryContent(
                                showSelectedWords = showSelectedWords,
                                sentences = sentences,
                                textForDisplay = if (state.isRussian) state.russianDisplayVersion else state.englishDisplayVersion,
                                currentSpokenWord = state.currentSpokenWord,
                                isSpeaking = state.isSpeaking,
                                highlightedSentence = highlightedSentence,
                                lastHighlightedSentence = state.lastHighlightedSentence,
                                scrollState = scrollState,
                                onSentenceClick = { sentence ->
                                    highlightedSentence = if (highlightedSentence == sentence) "" else sentence
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
                                }
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