// app/src/main/java/com/example/gem/DictionaryScreen.kt

package com.example.gem

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gem.data.Word
import java.io.InputStream
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(
    viewModel: DictionaryViewModel,
    onImportClick: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when (val state = uiState) {
                        is DictionaryUiState.Success -> Text("Dictionary (${state.words.size} words)")
                        DictionaryUiState.Initial,
                        DictionaryUiState.Loading,
                        is DictionaryUiState.Error -> Text("Dictionary")
                    }
                },
                actions = {
                    IconButton(onClick = onImportClick) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Import")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add word")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues)
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.filterWords(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search words...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when (val state = uiState) {
                    is DictionaryUiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    "Importing dictionary...",
                                    color = Color.White
                                )
                            }
                        }
                    }
                    is DictionaryUiState.Success -> {
                        LazyColumn {
                            items(state.words) { word ->
                                WordCard(
                                    word = word,
                                    onPlayClick = { viewModel.playWord(word) }
                                )
                            }
                        }
                    }
                    is DictionaryUiState.Error -> {
                        Text(
                            text = state.message,
                            modifier = Modifier.padding(16.dp),
                            color = Color.Red
                        )
                    }
                    DictionaryUiState.Initial -> {
                        // Initial state - can show welcome message
                    }
                }
            }
        }

        if (showAddDialog) {
            var english by remember { mutableStateOf("") }
            var russian by remember { mutableStateOf("") }
            var transcription by remember { mutableStateOf("") }
            var example by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add new word") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = english,
                            onValueChange = { english = it },
                            label = { Text("English") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = russian,
                            onValueChange = { russian = it },
                            label = { Text("Russian") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = transcription,
                            onValueChange = { transcription = it },
                            label = { Text("Transcription") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = example,
                            onValueChange = { example = it },
                            label = { Text("Example") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.addWord(
                                Word(
                                    english = english,
                                    russian = russian,
                                    transcription = transcription,
                                    example = example
                                )
                            )
                            showAddDialog = false
                        }
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordCard(
    word: Word,
    onPlayClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = word.english,
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = onPlayClick) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                }
            }
            if (word.transcription.isNotEmpty()) {
                Text(
                    text = word.transcription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text(
                text = word.russian,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (word.example.isNotEmpty()) {
                Text(
                    text = word.example,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}