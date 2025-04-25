package com.example.gem.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.example.gem.StoryViewModel
import com.example.gem.UiState
import com.example.gem.utils.isPunctuation

/**
 * Отображает оверлей загрузки с индикатором прогресса
 */
@Composable
fun LoadingOverlay(state: UiState.Loading) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(16.dp),
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

                if (state.attempt > 0) {
                    Text(
                        text = "Attempt ${state.attempt}/${state.maxAttempts}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (state.usedWordsCount > 0) {
                        Text(
                            text = "Used ${state.usedWordsCount} of ${state.totalWords} words",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    LinearProgressIndicator(
                        progress = state.attempt.toFloat() / state.maxAttempts,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Элементы управления для чтения истории и переключения между режимами отображения
 */
@Composable
fun StoryControls(
    title: String,
    subtitleInfo: String = "",
    showSelectedWords: Boolean,
    isSpeaking: Boolean,
    speechRate: Float,
    onToggleViewClick: () -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onPlayStopClick: () -> Unit
) {
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
                text = title,
                style = MaterialTheme.typography.titleMedium
            )

            // Номер предложения или другая дополнительная информация
            if (subtitleInfo.isNotEmpty()) {
                Text(
                    text = " ($subtitleInfo)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Правая часть: Элементы управления
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Кнопка показа/скрытия слов
            TextButton(
                onClick = onToggleViewClick,
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
                onValueChange = onSpeechRateChange,
                valueRange = 0.5f..1.1f,
                steps = 2,
                modifier = Modifier.width(100.dp)
            )

            // Кнопка воспроизведения/остановки
            IconButton(
                onClick = onPlayStopClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isSpeaking) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                    contentDescription = if (isSpeaking) "Stop reading" else "Read text",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Карточка с сообщением об ошибке и кнопкой повтора
 */
@Composable
fun ErrorCard(
    errorMessage: String,
    onRetryClick: () -> Unit
) {
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
                text = errorMessage,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onRetryClick
            ) {
                Text("Retry")
            }
        }
    }
}

/**
 * Карточка начального состояния с приглашением создать историю
 */
@Composable
fun EmptyStateCard(
    onGenerateClick: () -> Unit
) {
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
                onClick = onGenerateClick
            ) {
                Text("Generate")
            }
        }
    }
}

/**
 * Вертикальный скроллбар для улучшения навигации
 */
@Composable
fun VerticalScrollbar(
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
    sentences: List<String>,
    currentSentenceIndex: Int
) {
    // Запоминаем высоту контейнера для расчета положения индикаторов
    var containerHeight by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(4.dp)
            .background(
                Color.LightGray.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.small
            )
            .onSizeChanged { containerHeight = it.height }
    ) {
        // Индикатор положения скролла (основной бегунок)
        if (scrollState.maxValue > 0 && containerHeight > 0) {
            val scrollbarHeight = 60.dp
            val scrollbarHeightPx = with(density) { scrollbarHeight.toPx() }
            val scrollPosition = scrollState.value.toFloat() / scrollState.maxValue.toFloat()

            // Вычисляем смещение, учитывая высоту скроллбара
            val offsetY = with(density) {
                ((containerHeight - scrollbarHeightPx) * scrollPosition).toDp()
            }

            Box(
                modifier = Modifier
                    .offset(y = offsetY)
                    .height(scrollbarHeight)
                    .width(4.dp)
                    .background(
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.small
                    )
                    .align(Alignment.TopStart)
            )
        }

        // Индикатор текущего предложения (желтый бегунок)
        if (sentences.isNotEmpty() && currentSentenceIndex >= 0 && containerHeight > 0) {
            val indicatorHeight = 20.dp
            val indicatorHeightPx = with(density) { indicatorHeight.toPx() }

            // Нормализованная позиция в пределах от 0 до 1
            val sentencePosition = if (sentences.size > 1) {
                currentSentenceIndex.toFloat() / (sentences.size - 1).toFloat()
            } else 0f

            // Вычисляем смещение для желтого индикатора
            val offsetY = with(density) {
                ((containerHeight - indicatorHeightPx) * sentencePosition).toDp()
            }

            Box(
                modifier = Modifier
                    .offset(y = offsetY)
                    .height(indicatorHeight)
                    .width(4.dp)
                    .background(
                        Color.Yellow.copy(alpha = 0.8f),
                        shape = MaterialTheme.shapes.small
                    )
                    .align(Alignment.TopStart)
            )
        }
    }
}

/**
 * Функция-обработчик для длительного нажатия на слове в тексте
 * Явно указываем тип возвращаемого значения, чтобы избежать ошибки компиляции
 */
fun handleWordLongPress(
    storyViewModel: StoryViewModel,
    onSelectedWordInfo: (String, Triple<String, String, String>) -> Unit
): (String, TextLayoutResult, ((Offset) -> Unit) -> Unit) -> Unit {
    return { text: String, layoutResult: TextLayoutResult, modifierWithDetection: ((Offset) -> Unit) -> Unit ->
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
                                Log.d("StoryScreen", "Tapped on word: '$cleanWord' at position $position")
                                storyViewModel.getWordInfoAndUpdate(cleanWord) { info ->
                                    onSelectedWordInfo(cleanWord, info)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("StoryScreen", "Error identifying word at position: ${e.message}", e)
                    // Fallback в случае ошибки - пытаемся выбрать случайное слово из предложения
                    val words = text.split(" ")
                    if (words.isNotEmpty()) {
                        val randomWord = words.random().replace(Regex("[^\\p{L}\\p{N}-]"), "")
                        if (randomWord.isNotEmpty()) {
                            storyViewModel.getWordInfoAndUpdate(randomWord) { info ->
                                onSelectedWordInfo(randomWord, info)
                            }
                        }
                    }
                }
            }

            // Передаем функцию в модификатор
            modifierWithDetection(longPressHandler)
        } catch (e: Exception) {
            Log.e("StoryScreen", "Error processing long press: ${e.message}", e)
        }
    }
}