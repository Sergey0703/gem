package com.example.gem

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.flowlayout.FlowRow
import com.google.accompanist.flowlayout.FlowCrossAxisAlignment
import com.google.accompanist.flowlayout.FlowMainAxisAlignment
import com.google.accompanist.flowlayout.SizeMode
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

// Расширение для Char для определения знаков пунктуации
fun Char.isPunctuation(): Boolean {
    return this in ".,;:!?\"'()[]{}<>«»—–-…/\\&@#$%^*_=+`~|"
}

val words = arrayOf(
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

@Composable
fun VerticalScrollbar(
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
    sentences: List<String> = emptyList(),
    currentSentenceIndex: Int = -1,
    width: Dp = 8.dp,
    thumbColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    if (scrollState.maxValue > 0) {
        Box(
            modifier = modifier
                .width(width)
                .fillMaxHeight()
                .background(trackColor, shape = RoundedCornerShape(4.dp))
        ) {
            // Расчет размера ползунка (соотношение)
            val thumbSizeRatio = (scrollState.viewportSize.toFloat() /
                    (scrollState.maxValue + scrollState.viewportSize))

            // Минимальный размер ползунка (15% от высоты)
            val minThumbSizeRatio = 0.15f

            // Используем максимальное из вычисленного размера и минимума
            val effectiveThumbSizeRatio = if (thumbSizeRatio < minThumbSizeRatio)
                minThumbSizeRatio else thumbSizeRatio

            // Рассчитываем максимальный диапазон перемещения (1.0 - размер ползунка)
            val availableScrollRange = 1f - effectiveThumbSizeRatio

            // Текущая позиция (от 0 до 1)
            val scrollRatio = if (scrollState.maxValue > 0)
                scrollState.value.toFloat() / scrollState.maxValue else 0f

            // Позиция ползунка (учитываем размер и не выходим за границы)
            val thumbPosition = scrollRatio * availableScrollRange

            // Рисуем ползунок
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(effectiveThumbSizeRatio)
                    .offset(y = (thumbPosition * 100).roundToInt().dp)
                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(4.dp))
                    .background(thumbColor, RoundedCornerShape(4.dp))
            )

            // Маркер текущего предложения (если есть)
            if (currentSentenceIndex >= 0 && sentences.isNotEmpty()) {
                // Позиция для маркера (от 0 до 1)
                val sentenceRatio = if (sentences.size > 1)
                    currentSentenceIndex.toFloat() / (sentences.size - 1) else 0f

                // Создаем маркер текущего предложения
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .offset(y = (sentenceRatio * 100).roundToInt().dp)
                        .background(
                            MaterialTheme.colorScheme.tertiary,
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}
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

    // Создаем состояние скролла, чтобы иметь к нему доступ для отображения скроллбара
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
    if (showWordDialog && selectedWord != null) {
        AlertDialog(
            onDismissRequest = {
                showWordDialog = false
                selectedWord = null
            },
            title = { Text(selectedWord ?: "") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Транскрипция
                    Text(
                        text = wordInfo.first,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )

                    // Перевод
                    Text(
                        text = wordInfo.second,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Пример использования
                    Text(
                        text = wordInfo.third,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Кнопка воспроизведения
                    IconButton(
                        onClick = {
                            selectedWord?.let { word ->
                                storyViewModel.speakWord(context, word)
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VolumeUp,
                            contentDescription = "Play pronunciation"
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showWordDialog = false
                    selectedWord = null
                }) {
                    Text("Close")
                }
            }
        )
    }
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = stringResource(R.string.app_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )

        when (val state = uiState) {
            is UiState.Loading -> {
                // Закомментировали верхнюю карточку с прогрессом, так как теперь используем модальное окно
            }
            is UiState.Success -> {
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
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Story generated successfully",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Length: ${if (state.isRussian) state.russianDisplayVersion.length else state.englishDisplayVersion.length}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Words used: ${state.selectedWords.size}/${state.selectedWords.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Generation time: ${state.generationTime}s",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { storyViewModel.toggleLanguage() },
                                enabled = !state.isTranslating
                            ) {
                                if (state.isTranslating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Language,
                                        contentDescription = "Toggle language"
                                    )
                                }
                            }

                            Button(
                                onClick = { storyViewModel.startStoryGeneration("") }
                            ) {
                                Text("Generate")
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
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
                                .padding(16.dp)
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
                            val sentenceCounterText = if (currentSentenceIndex > 0 && totalSentences > 0)
                                " (${currentSentenceIndex}/${totalSentences})" else ""

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (showSelectedWords) {
                                        if (!state.isRussian) "Selected words:" else "Выбранные слова:"
                                    } else {
                                        if (!state.isRussian) "Story:$sentenceCounterText" else "История:$sentenceCounterText"
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { showSelectedWords = !showSelectedWords },
                                        modifier = Modifier
                                    ) {
                                        Text(if (showSelectedWords) "Show story" else "Show words")
                                    }

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

                                    IconButton(
                                        onClick = {
                                            if (state.isSpeaking) {
                                                storyViewModel.stopSpeaking()
                                            } else {
                                                if (showSelectedWords) {
                                                    // Воспроизведение выбранных слов в режиме просмотра слов
                                                    storyViewModel.speakSelectedWords(context, state.selectedWords)
                                                } else {
                                                    // Обычное воспроизведение текста в режиме просмотра истории
                                                    storyViewModel.speakTextWithHighlight(
                                                        context,
                                                        if (state.isRussian) state.russianVersion else state.englishVersion,
                                                        highlightedSentence
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.padding(start = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (state.isSpeaking) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                                            contentDescription = if (state.isSpeaking) "Stop reading" else "Read text with highlighting"
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = !showSelectedWords,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                // Контейнер с текстом и скроллбаром
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                ) {
                                    // Контент с прокруткой
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(scrollState)
                                            .padding(end = 12.dp) // Отступ для скроллбара
                                    ) {
                                        if (state.isTranslating) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator()
                                            }
                                        } else {
                                            // Для отображения используем displayVersion, которая уже без разделителей
                                            val textForDisplay = if (state.isRussian) state.russianDisplayVersion else state.englishDisplayVersion
                                            // Для разбиения на предложения и подсветки используем версию с разделителями
                                            val currentSpokenWord = state.currentSpokenWord
                                            val isSpeaking = state.isSpeaking

                                            Column {
                                                FlowRow(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    mainAxisAlignment = FlowMainAxisAlignment.Start,
                                                    crossAxisAlignment = FlowCrossAxisAlignment.Start,
                                                    mainAxisSize = SizeMode.Wrap,
                                                    crossAxisSpacing = 8.dp,
                                                    mainAxisSpacing = 8.dp
                                                ) {
                                                    // Отображаем предложения с подсветкой
                                                    if (sentences.size > 1) {
                                                        // Разбиение на предложения успешно сработало, отображаем их с подсветкой
                                                        sentences.forEach { sentence ->
                                                            // Определяем, нужно ли подсвечивать текущее предложение
                                                            val shouldHighlight = when {
                                                                // Когда идет чтение, подсвечиваем только текущее произносимое предложение
                                                                isSpeaking && currentSpokenWord.isNotEmpty() &&
                                                                        currentSpokenWord.replace(sentenceSeparator, "").trim() == sentence.replace(sentenceSeparator, "").trim() -> true

                                                                // Когда чтение остановлено:
                                                                // Подсвечиваем либо последнее прочитанное предложение, либо выбранное пользователем
                                                                !isSpeaking && (
                                                                        (state.lastHighlightedSentence.isNotEmpty() &&
                                                                                state.lastHighlightedSentence.replace(sentenceSeparator, "").trim() == sentence.replace(sentenceSeparator, "").trim()) ||
                                                                                (highlightedSentence.isNotEmpty() &&
                                                                                        highlightedSentence.replace(sentenceSeparator, "").trim() == sentence.replace(sentenceSeparator, "").trim())
                                                                        ) -> true

                                                                else -> false
                                                            }

                                                            // Отображаем предложение без разделителей
                                                            val cleanSentence = sentence.replace(sentenceSeparator, "")

                                                            // Для отслеживания расположения текста и выделения конкретных слов
                                                            var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                                                            Text(
                                                                text = cleanSentence + " ",
                                                                style = MaterialTheme.typography.bodyLarge,
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                overflow = TextOverflow.Visible,
                                                                onTextLayout = { textLayoutResult = it },
                                                                modifier = Modifier
                                                                    .pointerInput(Unit) {
                                                                        detectTapGestures(
                                                                            onTap = {
                                                                                // Обработка нажатия только если чтение не активно
                                                                                if (!isSpeaking) {
                                                                                    highlightedSentence = if (highlightedSentence == sentence) "" else sentence
                                                                                }
                                                                            },
                                                                            onLongPress = { offset ->
                                                                                try {
                                                                                    // Находим конкретное слово, на которое нажал пользователь
                                                                                    val layoutResult = textLayoutResult
                                                                                    if (layoutResult != null) {
                                                                                        // Находим позицию символа под координатой нажатия
                                                                                        val position = layoutResult.getOffsetForPosition(offset)

                                                                                        // Находим границы слова
                                                                                        val text = cleanSentence
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
                                                                                    } else {
                                                                                        // Fallback, если layoutResult недоступен
                                                                                        val words = cleanSentence.split(" ")
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
                                                                                } catch (e: Exception) {
                                                                                    Log.e("BakingScreen", "Error processing long press: ${e.message}", e)
                                                                                }
                                                                            }
                                                                        )
                                                                    }
                                                                    .then(
                                                                        if (shouldHighlight) {
                                                                            Modifier.background(Color.Yellow.copy(alpha = 0.3f))
                                                                        } else {
                                                                            Modifier
                                                                        }
                                                                    )
                                                            )
                                                        }
                                                    } else {
                                                        // Если разбиение не удалось, отображаем полный текст для отображения
                                                        // и добавляем функциональность выделения слов при долгом нажатии
                                                        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                                                        Text(
                                                            text = textForDisplay,
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            overflow = TextOverflow.Visible,
                                                            onTextLayout = { textLayoutResult = it },
                                                            modifier = Modifier
                                                                .pointerInput(Unit) {
                                                                    detectTapGestures(
                                                                        onTap = {
                                                                            // При отсутствии предложений, выделяем целый текст
                                                                            if (!isSpeaking) {
                                                                                highlightedSentence = if (highlightedSentence.isNotEmpty()) "" else textForDisplay
                                                                            }
                                                                        },
                                                                        onLongPress = { offset ->
                                                                            try {
                                                                                val layoutResult = textLayoutResult
                                                                                if (layoutResult != null) {
                                                                                    // Находим позицию символа под координатой нажатия
                                                                                    val position = layoutResult.getOffsetForPosition(offset)

                                                                                    // Проверяем, что позиция находится в пределах текста
                                                                                    if (position >= 0 && position < textForDisplay.length) {
                                                                                        // Находим границы слова
                                                                                        val text = textForDisplay
                                                                                        var wordStart = position
                                                                                        var wordEnd = position

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
                                                                                } else {
                                                                                    // Fallback, если layoutResult недоступен
                                                                                    val words = textForDisplay.split(" ")
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
                                                                            } catch (e: Exception) {
                                                                                Log.e("BakingScreen", "Error processing long press on full text: ${e.message}", e)
                                                                            }
                                                                        }
                                                                    )
                                                                }
                                                                .then(
                                                                    if (currentSpokenWord.isNotEmpty()) {
                                                                        Modifier.background(Color.Yellow.copy(alpha = 0.3f))
                                                                    } else {
                                                                        Modifier
                                                                    }
                                                                )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Улучшенный вертикальный скроллбар
                                    VerticalScrollbar(
                                        scrollState = scrollState,
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .fillMaxHeight(),
                                        sentences = sentences,
                                        currentSentenceIndex = currentSentenceIndex - 1
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = showSelectedWords,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                // Контейнер с сеткой слов и скроллбаром
                                Box(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    LazyVerticalGrid(
                                        columns = GridCells.Adaptive(minSize = 100.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(top = 8.dp, end = 12.dp) // Отступ для скроллбара
                                    ) {
                                        items(state.selectedWords) { word ->
                                            Surface(
                                                color = if (state.currentSpokenWord == word)
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                                else
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                shape = MaterialTheme.shapes.small,
                                                modifier = Modifier.clickable {
                                                    selectedWord = word
                                                    storyViewModel.getWordInfoAndUpdate(word) { (transcription, translation, example) ->
                                                        wordInfo = Triple(transcription, translation, example)
                                                        showWordDialog = true
                                                    }
                                                }
                                            ) {
                                                Text(
                                                    text = word,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            }
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

@Preview(showSystemUi = true)
@Composable
fun StoryScreenPreview() {
    StoryScreen()
}