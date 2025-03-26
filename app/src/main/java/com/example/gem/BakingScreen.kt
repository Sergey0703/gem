package com.example.gem

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

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
    storyViewModel: StoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val placeholderPrompt = stringResource(R.string.prompt_placeholder)
    val placeholderResult = stringResource(R.string.results_placeholder)
    var prompt by rememberSaveable { mutableStateOf(placeholderPrompt) }
    var result by rememberSaveable { mutableStateOf(placeholderResult) }
    val uiState by storyViewModel.uiState.collectAsState()
    
    var selectedWord by remember { mutableStateOf<String?>(null) }
    var showWordDialog by remember { mutableStateOf(false) }

    // Инициализация TextToSpeech
    LaunchedEffect(Unit) {
        storyViewModel.initializeTTS(context)
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = stringResource(R.string.app_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = prompt,
                onValueChange = { newValue -> prompt = newValue },
                label = { Text(stringResource(R.string.label_prompt)) },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Кнопка переключения языка
                IconButton(
                    onClick = { 
                        storyViewModel.toggleLanguage(prompt)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Language,
                        contentDescription = "Toggle language"
                    )
                }

                Button(
                    onClick = {
                        storyViewModel.generateStory(prompt)
                    },
                    enabled = prompt.isNotEmpty()
                ) {
                    Text(text = stringResource(R.string.action_go))
                }
            }
        }

        if (uiState is UiState.Loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            var textColor = MaterialTheme.colorScheme.onSurface
            if (uiState is UiState.Error) {
                textColor = MaterialTheme.colorScheme.error
                result = (uiState as UiState.Error).errorMessage
            } else if (uiState is UiState.Success) {
                textColor = MaterialTheme.colorScheme.onSurface
                val successState = uiState as UiState.Success
                result = successState.outputText
                
                // Показываем выбранные слова
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = if (!successState.isRussian) "Selected words:" else "Выбранные слова:",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            successState.selectedWords.forEach { word ->
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.clickable {
                                        selectedWord = word
                                        showWordDialog = true
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
                
                // Создаем текст с выделенными словами
                val highlightedText = buildAnnotatedString {
                    val text = successState.outputText
                    var currentIndex = 0
                    
                    while (currentIndex < text.length) {
                        var foundWord = false
                        for (word in successState.selectedWords) {
                            if (text.startsWith(word, currentIndex, ignoreCase = true)) {
                                val start = currentIndex
                                val end = currentIndex + word.length
                                append(text.substring(start, end))
                                addStyle(
                                    SpanStyle(
                                        background = MaterialTheme.colorScheme.primaryContainer,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    start,
                                    end
                                )
                                currentIndex = end
                                foundWord = true
                                break
                            }
                        }
                        if (!foundWord) {
                            append(text[currentIndex])
                            currentIndex++
                        }
                    }
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (!successState.isRussian) "Story text:" else "Текст истории:",
                        style = MaterialTheme.typography.titleSmall
                    )
                    IconButton(
                        onClick = { storyViewModel.speakText(successState.outputText) }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VolumeUp,
                            contentDescription = "Speak text"
                        )
                    }
                }
                
                Text(
                    text = highlightedText,
                    textAlign = TextAlign.Start,
                    color = textColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                )
            } else {
                Text(
                    text = result,
                    textAlign = TextAlign.Start,
                    color = textColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }

    // Показываем диалог с информацией о слове
    if (showWordDialog && selectedWord != null) {
        val wordInfo = storyViewModel.getWordInfo(selectedWord!!)
        WordDialog(
            word = selectedWord!!,
            transcription = wordInfo.first,
            translation = wordInfo.second,
            example = wordInfo.third,
            onDismiss = {
                showWordDialog = false
                selectedWord = null
            },
            onSpeak = {
                storyViewModel.speakWord(selectedWord!!)
            }
        )
    }
}

@Preview(showSystemUi = true)
@Composable
fun StoryScreenPreview() {
    StoryScreen()
}