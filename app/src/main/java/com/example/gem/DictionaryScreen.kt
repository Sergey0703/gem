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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gem.data.Word
import java.io.InputStream
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Sort
import java.text.SimpleDateFormat
import java.util.*

enum class SortOption {
    NAME,
    LAST_USED,
    DATE_ADDED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(
    viewModel: DictionaryViewModel,
    onImportClick: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedWord by remember { mutableStateOf<Word?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var currentSort by remember { mutableStateOf(SortOption.DATE_ADDED) }
    var sortAscending by remember { mutableStateOf(false) }
    var isSorting by remember { mutableStateOf(false) } // State for tracking sorting operation

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Effect to reset sorting indicator when UI state changes
    LaunchedEffect(uiState) {
        if (uiState is DictionaryUiState.Success) {
            isSorting = false
        }
    }

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
                    // Sort button with loading indicator
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        // Show sorting indicator when active
                        if (isSorting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            // Sort button
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Outlined.Sort, contentDescription = "Sort")
                            }
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Name ${if(currentSort == SortOption.NAME) if(sortAscending) "↑" else "↓" else ""}") },
                                onClick = {
                                    isSorting = true // Show loading indicator
                                    if (currentSort == SortOption.NAME) {
                                        sortAscending = !sortAscending
                                    } else {
                                        currentSort = SortOption.NAME
                                        sortAscending = true
                                    }
                                    viewModel.sortWords(currentSort, sortAscending)
                                    showSortMenu = false
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Last used ${if(currentSort == SortOption.LAST_USED) if(sortAscending) "↑" else "↓" else ""}") },
                                onClick = {
                                    isSorting = true // Show loading indicator
                                    if (currentSort == SortOption.LAST_USED) {
                                        sortAscending = !sortAscending
                                    } else {
                                        currentSort = SortOption.LAST_USED
                                        sortAscending = false
                                    }
                                    viewModel.sortWords(currentSort, sortAscending)
                                    showSortMenu = false
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Date added ${if(currentSort == SortOption.DATE_ADDED) if(sortAscending) "↑" else "↓" else ""}") },
                                onClick = {
                                    isSorting = true // Show loading indicator
                                    if (currentSort == SortOption.DATE_ADDED) {
                                        sortAscending = !sortAscending
                                    } else {
                                        currentSort = SortOption.DATE_ADDED
                                        sortAscending = false
                                    }
                                    viewModel.sortWords(currentSort, sortAscending)
                                    showSortMenu = false
                                }
                            )
                        }
                    }

                    // Import button
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
                        // Show loading overlay during sorting if needed
                        if (isSorting) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        LazyColumn {
                            items(state.words) { word ->
                                WordCard(
                                    word = word,
                                    onPlayClick = { viewModel.playWord(word) },
                                    onEditClick = {
                                        selectedWord = word
                                        showEditDialog = true
                                    }
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

        // Add word dialog
        if (showAddDialog) {
            WordDialog(
                title = "Add new word",
                initialWord = Word(english = "", russian = "", transcription = "", example = ""),
                onDismiss = { showAddDialog = false },
                onSave = { word ->
                    viewModel.addWord(word)
                    showAddDialog = false
                },
                showDelete = false,
                onDelete = { }
            )
        }

        // Edit word dialog with delete option
        if (showEditDialog && selectedWord != null) {
            WordDialog(
                title = "Edit word",
                initialWord = selectedWord!!,
                onDismiss = { showEditDialog = false },
                onSave = { word ->
                    viewModel.updateWord(word)
                    showEditDialog = false
                },
                showDelete = true,
                onDelete = {
                    viewModel.deleteWord(selectedWord!!)
                    Toast.makeText(context, "Word deleted", Toast.LENGTH_SHORT).show()
                    showEditDialog = false
                }
            )
        }
    }
}

@Composable
fun WordDialog(
    title: String,
    initialWord: Word,
    onDismiss: () -> Unit,
    onSave: (Word) -> Unit,
    showDelete: Boolean,
    onDelete: () -> Unit
) {
    var english by remember { mutableStateOf(initialWord.english) }
    var russian by remember { mutableStateOf(initialWord.russian) }
    var transcription by remember { mutableStateOf(initialWord.transcription) }
    var example by remember { mutableStateOf(initialWord.example) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete word") },
            text = { Text("Are you sure you want to delete the word '${initialWord.english}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirmation = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title)
        },
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
        // Three buttons in bottom row: Delete (left), Cancel (middle), Save (right)
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Delete button on the left (only if editing)
                if (showDelete) {
                    TextButton(
                        onClick = { showDeleteConfirmation = true },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                } else {
                    // Spacer to maintain layout when delete button is not shown
                    Spacer(Modifier.width(64.dp))
                }

                // Cancel button in the middle
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }

                // Save button on the right
                TextButton(
                    onClick = {
                        val updatedWord = initialWord.copy(
                            english = english,
                            russian = russian,
                            transcription = transcription,
                            example = example
                        )
                        onSave(updatedWord)
                    }
                ) {
                    Text("Save")
                }
            }
        },
        // Need to provide an empty dismissButton to avoid duplicating the Cancel button
        dismissButton = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordCard(
    word: Word,
    onPlayClick: () -> Unit,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp), // Уменьшенный вертикальный отступ для меньшей высоты
        elevation = CardDefaults.cardElevation()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp) // Уменьшенный вертикальный отступ внутри карточки
        ) {
            // Колонка с правыми кнопками - выровнена по правому краю
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Увеличенная кнопка воспроизведения
                IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier.size(48.dp) // Увеличили размер
                ) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = "Pronounce",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp) // Увеличили размер иконки
                    )
                }

                // Кнопка редактирования прямо под кнопкой воспроизведения
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(48.dp) // Увеличили размер
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp) // Размер иконки редактирования
                    )
                }
            }

            // Основной контент (словарная информация)
            Column(
                modifier = Modifier
                    .padding(start = 12.dp, end = 100.dp, top = 4.dp, bottom = 4.dp) // Увеличенное пространство справа для кнопок
                    .fillMaxWidth()
            ) {
                // Word title
                Text(
                    text = word.english,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Transcription
                if (word.transcription.isNotEmpty()) {
                    Text(
                        text = word.transcription,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 1.dp) // Уменьшенный отступ
                    )
                }

                // Translation
                Text(
                    text = word.russian,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 2.dp) // Уменьшенный отступ
                )

                // Example
                if (word.example.isNotEmpty()) {
                    Text(
                        text = word.example,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 2.dp), // Уменьшенный отступ
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Дата в одну строку
                Text(
                    text = buildString {
                        append("Added: ${formatDate(word.dateAdded)}")
                        word.lastUsed?.let {
                            append(" • Used: ${formatDate(it)}")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 3.dp) // Уменьшенный отступ
                )
            }
        }
    }
}

// Helper function to format dates
private fun formatDate(date: Date): String {
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return dateFormat.format(date)
}