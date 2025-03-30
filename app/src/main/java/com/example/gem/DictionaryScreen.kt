package com.example.gem

import android.net.Uri
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

@Composable
fun DictionaryScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Dictionary",
            style = MaterialTheme.typography.titleLarge
        )
        
        // Placeholder for dictionary content
        Text(
            text = "Dictionary content will be implemented here",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(
    paddingValues: PaddingValues,
    viewModel: DictionaryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingWord by remember { mutableStateOf<Word?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { inputStream ->
                viewModel.importCsv(inputStream)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
    ) {
        // Поисковая строка
        TextField(
            value = searchQuery,
            onValueChange = { 
                searchQuery = it
                viewModel.filterWords(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            placeholder = { Text("Search words...") },
            singleLine = true
        )

        // Кнопка импорта CSV
        Button(
            onClick = { launcher.launch("text/csv") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text("Import CSV File")
        }

        // Список слов
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (uiState) {
                is DictionaryUiState.Success -> {
                    items((uiState as DictionaryUiState.Success).words) { word ->
                        WordItem(
                            word = word,
                            onEditClick = { editingWord = word },
                            onDeleteClick = { viewModel.deleteWord(word) },
                            onSpeakClick = { /* Implementation needed */ }
                        )
                    }
                }
                is DictionaryUiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                is DictionaryUiState.Error -> {
                    item {
                        Text(
                            text = (uiState as DictionaryUiState.Error).message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                else -> {}
            }
        }

        // Кнопка добавления
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.End)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add word")
        }
    }

    // Диалог добавления/редактирования слова
    if (showAddDialog || editingWord != null) {
        WordDialog(
            word = editingWord,
            onDismiss = { 
                showAddDialog = false
                editingWord = null
            },
            onSave = { english, russian, transcription, example ->
                if (editingWord != null) {
                    viewModel.updateWord(editingWord!!.copy(
                        english = english,
                        russian = russian,
                        transcription = transcription,
                        example = example
                    ))
                } else {
                    viewModel.addWord(Word(
                        english = english,
                        russian = russian,
                        transcription = transcription,
                        example = example
                    ))
                }
                showAddDialog = false
                editingWord = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordItem(
    word: Word,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSpeakClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = word.english,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = word.russian,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Row {
                    IconButton(onClick = onSpeakClick) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Speak")
                    }
                    IconButton(onClick = onEditClick) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
            if (word.transcription.isNotEmpty()) {
                Text(
                    text = word.transcription,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (word.example.isNotEmpty()) {
                Text(
                    text = word.example,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDialog(
    word: Word?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var english by remember { mutableStateOf(word?.english ?: "") }
    var russian by remember { mutableStateOf(word?.russian ?: "") }
    var transcription by remember { mutableStateOf(word?.transcription ?: "") }
    var example by remember { mutableStateOf(word?.example ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (word == null) "Add Word" else "Edit Word") },
        text = {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = english,
                    onValueChange = { english = it },
                    label = { Text("English") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = russian,
                    onValueChange = { russian = it },
                    label = { Text("Russian") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = transcription,
                    onValueChange = { transcription = it },
                    label = { Text("Transcription") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = example,
                    onValueChange = { example = it },
                    label = { Text("Example") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(english, russian, transcription, example) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
} 