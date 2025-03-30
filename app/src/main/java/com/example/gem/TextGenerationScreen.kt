package com.example.gem

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextGenerationScreen(
    paddingValues: PaddingValues,
    viewModel: StoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var speechRate by remember { mutableStateOf(1.0f) }
    var isSpeaking by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
    ) {
        // Кнопка генерации истории
        Button(
            onClick = { viewModel.startStoryGeneration("") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text("Generate Story")
        }

        // Отображение состояния и контента
        when (val state = uiState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator()
                        if (state.attempt > 0) {
                            Text(
                                text = "Attempt ${state.attempt} of ${state.maxAttempts}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (state.usedWordsCount > 0) {
                                Text(
                                    text = "Used ${state.usedWordsCount} of ${state.totalWords} words",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
            is UiState.Success -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Информация о генерации
                    if (state.generationTime > 0) {
                        Text(
                            text = "Generated in ${String.format("%.1f", state.generationTime)} seconds",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    // Текст истории
                    Text(
                        text = if (state.isRussian) state.russianVersion else state.englishVersion,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                // Панель управления
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Кнопка переключения языка
                    IconButton(
                        onClick = { viewModel.toggleLanguage() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Toggle language"
                        )
                    }

                    // Кнопка воспроизведения
                    IconButton(
                        onClick = {
                            isSpeaking = !isSpeaking
                            if (isSpeaking) {
                                viewModel.speakText(
                                    context,
                                    if (state.isRussian) state.russianVersion else state.englishVersion
                                )
                            } else {
                                viewModel.stopSpeaking()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isSpeaking) "Stop" else "Play"
                        )
                    }

                    // Слайдер скорости речи
                    Slider(
                        value = speechRate,
                        onValueChange = {
                            speechRate = it
                            viewModel.setSpeechRate(it)
                        },
                        valueRange = 0.5f..2.0f,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            is UiState.Error -> {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
            else -> {
                // Initial state
                Text(
                    text = "Press 'Generate Story' to start",
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }
    }
} 