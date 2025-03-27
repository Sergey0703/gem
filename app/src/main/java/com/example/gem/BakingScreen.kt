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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically

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
    var showSelectedWords by remember { mutableStateOf(false) }
    var wordInfo by remember { mutableStateOf(Triple("", "", "")) }

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
                IconButton(
                    onClick = { storyViewModel.toggleLanguage() }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Language,
                        contentDescription = "Toggle language"
                    )
                }

                Button(
                    onClick = { storyViewModel.startStoryGeneration(prompt) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Generate")
                }
            }
        }

        when (val state = uiState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Single card for both story and words
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
                                        onClick = { showSelectedWords = !showSelectedWords }
                                    ) {
                                        Text(if (showSelectedWords) "Show story" else "Show words")
                                    }
                                    IconButton(
                                        onClick = {
                                            storyViewModel.speakText(
                                                context,
                                                if (state.isRussian) state.russianVersion else state.englishVersion
                                            )
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.VolumeUp,
                                            contentDescription = "Read text"
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
                                    val text = if (state.isRussian) state.russianVersion else state.englishVersion
                                    val annotatedText = buildAnnotatedString {
                                        var currentIndex = 0
                                        val pattern = Regex("""\*\*([^*]+)\*\*|\*([^*]+)\*""")
                                        
                                        pattern.findAll(text).forEach { matchResult ->
                                            // Add text before the match
                                            append(text.substring(currentIndex, matchResult.range.first))
                                            
                                            // Get the word without asterisks
                                            val word = matchResult.groupValues[1].ifEmpty { matchResult.groupValues[2] }
                                            
                                            // Add the word with special style
                                            withStyle(
                                                style = SpanStyle(
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            ) {
                                                append(word)
                                            }
                                            
                                            currentIndex = matchResult.range.last + 1
                                        }
                                        
                                        // Add remaining text
                                        if (currentIndex < text.length) {
                                            append(text.substring(currentIndex))
                                        }
                                    }
                                    
                                    Text(
                                        text = annotatedText,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                    )
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
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
            else -> {
                Text(
                    text = "Enter your prompt and press Generate",
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }

    // Показываем диалог с информацией о слове
    if (showWordDialog && selectedWord != null) {
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
                storyViewModel.speakWord(context, selectedWord!!)
            }
        )
    }
}

@Preview(showSystemUi = true)
@Composable
fun StoryScreenPreview() {
    StoryScreen()
}