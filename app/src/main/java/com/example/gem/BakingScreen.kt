package com.example.gem

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gem.ui.components.StoryContent
import com.example.gem.ui.components.StoryHeader
import com.example.gem.ui.components.StoryWordInfoDialog
import com.example.gem.utils.isPunctuation

@Composable
fun StoryScreen(
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

    // Спиннер на весь экран во время генерации
    if (isGenerating) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .widthIn(max = 300.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(56.dp)
                    )

                    Text(
                        text = "Generating story...",
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (uiState is UiState.Loading) {
                        val loadingState = uiState as UiState.Loading
                        if (loadingState.attempt > 0) {
                            Text(
                                text = "Attempt ${loadingState.attempt}/${loadingState.maxAttempts}",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            if (loadingState.usedWordsCount > 0) {
                                Text(
                                    text = "Used ${loadingState.usedWordsCount} of ${loadingState.totalWords} words",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            LinearProgressIndicator(
                                progress = loadingState.attempt.toFloat() / loadingState.maxAttempts,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    // Диалог с информацией о слове
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

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Уменьшенный отступ для заголовка
        Text(
            text = stringResource(R.string.app_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        when (val state = uiState) {
            is UiState.Loading -> {
                // Содержимое уже отображается в модальном окне
            }
            is UiState.Success -> {
                // Заголовок с информацией
                StoryHeader(
                    state = state,
                    onGenerateClick = { storyViewModel.startStoryGeneration("") },
                    onLanguageToggleClick = { storyViewModel.toggleLanguage() }
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    // Story display card
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
                            // Вычисляем текущее предложение и общее количество
                            val textWithSeparators = if (state.isRussian) state.russianVersion else state.englishVersion
                            val sentences = textWithSeparators.split(sentenceSeparator)
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }

                            val currentSentenceIndex = sentences.indexOfFirst { sentence ->
                                val cleanSentence = sentence.replace(sentenceSeparator, "").trim()
                                val stateSentence = state.currentSpokenWord.replace(sentenceSeparator, "").trim()
                                cleanSentence == stateSentence
                            }.let { if (it >= 0) it + 1 else 0 }

                            val totalSentences = sentences.size

                            // Заголовок с элементами управления
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Левая часть: Заголовок с номером предложения
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (showSelectedWords) {
                                            if (!state.isRussian) "Selected words:" else "Выбранные слова:"
                                        } else {
                                            if (!state.isRussian) "Story:" else "История:"
                                        },
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    // Номер предложения только если показываем историю и есть текущее предложение
                                    if (!showSelectedWords && currentSentenceIndex > 0 && totalSentences > 0) {
                                        Text(
                                            text = " ($currentSentenceIndex/$totalSentences)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Правая часть: Элементы управления
                                Row(
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                    // Убираем wrapContentWidth - это и была первая ошибка
                                ) {
                                    // Кнопка показа/скрытия слов
                                    TextButton(
                                        onClick = { showSelectedWords = !showSelectedWords },
                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                        modifier = Modifier.padding(end = 2.dp)
                                    ) {
                                        Text(
                                            text = if (showSelectedWords) "Show story" else "Show words",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    // Слайдер скорости чтения
                                    Slider(
                                        value = speechRate,
                                        onValueChange = {
                                            speechRate = it
                                            storyViewModel.setSpeechRate(it)
                                        },
                                        valueRange = 0.5f..1.1f,
                                        steps = 2,
                                        modifier = Modifier.width(100.dp)
                                    )

                                    // Кнопка воспроизведения/остановки
                                    IconButton(
                                        onClick = {
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
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (state.isSpeaking) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                                            contentDescription = if (state.isSpeaking) "Stop reading" else "Read text",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            // Основное содержимое - текст истории или сетка слов
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
                                // Исправляем передачу функции для обработки долгого нажатия
                                onWordLongPress = { text, layoutResult, modifierWithDetection ->
                                    try {
                                        // Создаем функцию для обработки долгого нажатия
                                        val longPressHandler = { offset: Offset ->
                                            try {
                                                // Находим позицию символа под координатой нажатия
                                                val position = layoutResult.getOffsetForPosition(offset)

                                                // Находим границы слова
                                                var wordStart = position
                                                var wordEnd = position

                                                // Проверяем, что позиция находится в пределах текста
                                                if (position >= 0 && position < text.length) {
                                                    // Определяем начало слова
                                                    while (wordStart > 0 &&
                                                        !text[wordStart - 1].isWhitespace() &&
                                                        !text[wordStart - 1].isPunctuation()) {
                                                        wordStart--
                                                    }

                                                    // Определяем конец слова
                                                    while (wordEnd < text.length &&
                                                        !text[wordEnd].isWhitespace() &&
                                                        !text[wordEnd].isPunctuation()) {
                                                        wordEnd++
                                                    }

                                                    // Проверяем, что диапазон не пуст
                                                    if (wordStart < wordEnd) {
                                                        // Извлекаем слово
                                                        val tappedWord = text.substring(wordStart, wordEnd)

                                                        // Очищаем слово от знаков пунктуации
                                                        val cleanWord = tappedWord.replace(Regex("[^\\p{L}\\p{N}-]"), "")

                                                        if (cleanWord.isNotEmpty()) {
                                                            Log.d("BakingScreen", "Tapped on word: '$cleanWord' at position $position")
                                                            selectedWord = cleanWord
                                                            storyViewModel.getWordInfoAndUpdate(cleanWord) { info ->
                                                                wordInfo = info
                                                                showWordDialog = true
                                                            }
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e("BakingScreen", "Error identifying word at position: ${e.message}", e)
                                                // Fallback в случае ошибки - пытаемся выбрать случайное слово из предложения
                                                val words = text.split(" ")
                                                if (words.isNotEmpty()) {
                                                    val randomWord = words.random().replace(Regex("[^\\p{L}\\p{N}-]"), "")
                                                    if (randomWord.isNotEmpty()) {
                                                        selectedWord = randomWord
                                                        storyViewModel.getWordInfoAndUpdate(randomWord) { info ->
                                                            wordInfo = info
                                                            showWordDialog = true
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // Передаем функцию в модификатор
                                        modifierWithDetection(longPressHandler)
                                    } catch (e: Exception) {
                                        Log.e("BakingScreen", "Error processing long press: ${e.message}", e)
                                    }
                                },
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { storyViewModel.startStoryGeneration("") }
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
            else -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Press Generate to create a story",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { storyViewModel.startStoryGeneration("") }
                        ) {
                            Text("Generate")
                        }
                    }
                }
            }
        }
    }
}