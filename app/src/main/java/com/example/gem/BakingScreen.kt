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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.flowlayout.FlowRow
import com.google.accompanist.flowlayout.FlowCrossAxisAlignment
import com.google.accompanist.flowlayout.FlowMainAxisAlignment
import com.google.accompanist.flowlayout.SizeMode

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
fun StoryScreen(
    storyViewModel: StoryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by storyViewModel.uiState.collectAsState()

    var selectedWord by remember { mutableStateOf<String?>(null) }
    var showWordDialog by remember { mutableStateOf(false) }
    var showSelectedWords by remember { mutableStateOf(false) }
    var wordInfo by remember { mutableStateOf(Triple("", "", "")) }
    var highlightedSentence by remember { mutableStateOf("") }
    var speechRate by remember { mutableStateOf(0.8f) }
    var isGenerating by remember { mutableStateOf(false) }

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
                /*Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (state.attempt > 0) {
                            Text(
                                text = "Story generated - Attempt ${state.attempt}/${state.maxAttempts}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (state.storyLength > 0) {
                                Text(
                                    text = "Length: ${state.storyLength}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (state.usedWordsCount > 0) {
                                Text(
                                    text = "Used words count: ${state.usedWordsCount}/${state.totalWords}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (state.missingWords.isNotEmpty()) {
                                Text(
                                    text = "Missing words: ${state.missingWords.joinToString(", ")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            progress = state.attempt.toFloat() / state.maxAttempts
                        )
                    }
                }*/
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
                                text = "Length: ${state.englishVersion.length}",
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
                        .verticalScroll(rememberScrollState())
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
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
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TextButton(
                                        onClick = { showSelectedWords = !showSelectedWords },
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    ) {
                                        Text(if (showSelectedWords) "Show story" else "Show words")
                                    }
                                    Slider(
                                        value = speechRate,
                                        onValueChange = {
                                            speechRate = it
                                            storyViewModel.setSpeechRate(it)
                                        },
                                        valueRange = 0.4f..1.2f,
                                        steps = 8,
                                        modifier = Modifier
                                            .weight(1f)
                                            .align(Alignment.CenterVertically)
                                    )
                                    IconButton(
                                        onClick = {
                                            storyViewModel.speakTextWithHighlight(
                                                context,
                                                if (state.isRussian) state.russianVersion else state.englishVersion,
                                                highlightedSentence
                                            )
                                        },
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.VolumeUp,
                                            contentDescription = "Read text with highlighting"
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = !showSelectedWords,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    if (state.isTranslating) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator()
                                        }
                                    } else {
                                        val text = if (state.isRussian) state.russianVersion else state.englishVersion
                                        val currentSpokenWord = state.currentSpokenWord

                                        Column {
                                            FlowRow(
                                                modifier = Modifier.fillMaxWidth(),
                                                mainAxisAlignment = FlowMainAxisAlignment.Start,
                                                crossAxisAlignment = FlowCrossAxisAlignment.Start,
                                                mainAxisSize = SizeMode.Wrap,
                                                crossAxisSpacing = 8.dp,
                                                mainAxisSpacing = 8.dp
                                            ) {
                                                text.split(Regex("(?<=[.!?])\\s+(?=[A-ZА-Я])")).forEach { sentence ->
                                                    val shouldHighlight = (currentSpokenWord.isNotEmpty() &&
                                                            sentence.trim() == currentSpokenWord.trim()) ||
                                                            sentence.trim() == highlightedSentence.trim()

                                                    sentence.split(Regex("\\s+")).forEach { word ->
                                                        val cleanWord = word.trim().replace(Regex("[^\\p{L}\\p{N}-]"), "")
                                                        if (cleanWord.isNotEmpty()) {
                                                            Text(
                                                                text = word + " ",
                                                                style = MaterialTheme.typography.bodyLarge,
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                modifier = Modifier
                                                                    .pointerInput(Unit) {
                                                                        detectTapGestures(
                                                                            onTap = {
                                                                                highlightedSentence = if (highlightedSentence == sentence) "" else sentence
                                                                            },
                                                                            onLongPress = {
                                                                                selectedWord = cleanWord
                                                                                storyViewModel.getWordInfoAndUpdate(cleanWord) { info ->
                                                                                    wordInfo = info
                                                                                    showWordDialog = true
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
                                            }
                                        }
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = showSelectedWords,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 100.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(top = 8.dp)
                                ) {
                                    items(state.selectedWords) { word ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
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