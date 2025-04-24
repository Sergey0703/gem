package com.example.gem.ui.components

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gem.utils.isPunctuation
import com.google.accompanist.flowlayout.FlowRow
import com.google.accompanist.flowlayout.FlowCrossAxisAlignment
import com.google.accompanist.flowlayout.FlowMainAxisAlignment
import com.google.accompanist.flowlayout.SizeMode

@Composable
fun StoryContent(
    showSelectedWords: Boolean,
    sentences: List<String>,
    textForDisplay: String,
    currentSpokenWord: String,
    isSpeaking: Boolean,
    highlightedSentence: String,
    lastHighlightedSentence: String,
    scrollState: androidx.compose.foundation.ScrollState,
    onSentenceClick: (String) -> Unit,
    onWordLongPress: (String, TextLayoutResult, ((Offset) -> Unit) -> Unit) -> Unit,
    selectedWords: List<String>,
    onWordClick: (String) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        AnimatedVisibility(
            visible = !showSelectedWords,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            // Контейнер с текстом и скроллбаром
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Контент с прокруткой
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(end = 12.dp)
                ) {
                    // Для разбиения на предложения и подсветки используем версию с разделителями
                    if (sentences.size > 1) {
                        // Разбиение на предложения успешно сработало, отображаем их с подсветкой
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                mainAxisAlignment = FlowMainAxisAlignment.Start,
                                crossAxisAlignment = FlowCrossAxisAlignment.Start,
                                mainAxisSize = SizeMode.Wrap,
                                crossAxisSpacing = 8.dp,
                                mainAxisSpacing = 8.dp
                            ) {
                                sentences.forEach { sentence ->
                                    // Определяем, нужно ли подсвечивать текущее предложение
                                    val shouldHighlight = when {
                                        // Когда идет чтение, подсвечиваем только текущее произносимое предложение
                                        isSpeaking && currentSpokenWord.isNotEmpty() &&
                                                currentSpokenWord.replace("<<SENTENCE_END>>", "").trim() == sentence.replace("<<SENTENCE_END>>", "").trim() -> true

                                        // Когда чтение остановлено:
                                        // Подсвечиваем либо последнее прочитанное предложение, либо выбранное пользователем
                                        !isSpeaking && (
                                                (lastHighlightedSentence.isNotEmpty() &&
                                                        lastHighlightedSentence.replace("<<SENTENCE_END>>", "").trim() == sentence.replace("<<SENTENCE_END>>", "").trim()) ||
                                                        (highlightedSentence.isNotEmpty() &&
                                                                highlightedSentence.replace("<<SENTENCE_END>>", "").trim() == sentence.replace("<<SENTENCE_END>>", "").trim())
                                                ) -> true

                                        else -> false
                                    }

                                    // Отображаем предложение без разделителей
                                    val cleanSentence = sentence.replace("<<SENTENCE_END>>", "")

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
                                                            onSentenceClick(sentence)
                                                        }
                                                    },
                                                    onLongPress = { offset ->
                                                        try {
                                                            val layoutResult = textLayoutResult
                                                            if (layoutResult != null) {
                                                                // Исправляем функцию для обработки без suspend
                                                                onWordLongPress(cleanSentence, layoutResult) { handler ->
                                                                    handler(offset)
                                                                }
                                                            }
                                                        } catch (e: Exception) {
                                                            Log.e("StoryContent", "Error in onLongPress: ${e.message}", e)
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
                            }
                        }
                    } else {
                        // Если разбиение не удалось, отображаем полный текст
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
                                                onSentenceClick(if (highlightedSentence.isNotEmpty()) "" else textForDisplay)
                                            }
                                        },
                                        onLongPress = { offset ->
                                            try {
                                                val layoutResult = textLayoutResult
                                                if (layoutResult != null) {
                                                    // Исправляем функцию для обработки без suspend
                                                    onWordLongPress(textForDisplay, layoutResult) { handler ->
                                                        handler(offset)
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e("StoryContent", "Error in onLongPress for full text: ${e.message}", e)
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

                // Улучшенный вертикальный скроллбар
                VerticalScrollbar(
                    scrollState = scrollState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                    sentences = sentences,
                    currentSentenceIndex = if (sentences.isNotEmpty()) {
                        sentences.indexOfFirst { sentence ->
                            val cleanSentence = sentence.replace("<<SENTENCE_END>>", "").trim()
                            val stateSentence = currentSpokenWord.replace("<<SENTENCE_END>>", "").trim()
                            cleanSentence == stateSentence
                        }
                    } else -1
                )
            }
        }

        AnimatedVisibility(
            visible = showSelectedWords,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            // Контейнер с сеткой слов
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(selectedWords) { word ->
                    Surface(
                        color = if (currentSpokenWord == word)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        else
                            MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .clickable {
                                onWordClick(word)
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