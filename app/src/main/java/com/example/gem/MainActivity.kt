package com.example.gem

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.gem.ui.theme.GemTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GemTheme {
                var selectedItem by remember { mutableStateOf(0) }
                val items = listOf("Story", "Dictionary")
                val navController = rememberNavController()
                val viewModel: DictionaryViewModel = hiltViewModel()
                val context = LocalContext.current

                // Флаг для отслеживания операции импорта
                var importInProgress by remember { mutableStateOf(false) }

                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    if (uri != null) {
                        try {
                            importInProgress = true // Устанавливаем флаг, что начали импорт
                            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                viewModel.importCsv(inputStream)
                                Toast.makeText(context, "Starting import...", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            importInProgress = false // Сбрасываем флаг при ошибке
                            Toast.makeText(context, "Error reading file: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "No file selected", Toast.LENGTH_SHORT).show()
                    }
                }

                // Отслеживаем изменения состояния для показа Toast
                val uiState by viewModel.uiState.collectAsState()
                LaunchedEffect(uiState) {
                    when (uiState) {
                        is DictionaryUiState.Error -> {
                            // При ошибке показываем сообщение и сбрасываем флаг импорта
                            importInProgress = false
                            Toast.makeText(
                                context,
                                (uiState as DictionaryUiState.Error).message,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is DictionaryUiState.Success -> {
                            // Показываем Toast только если импорт был запущен и сейчас завершился
                            if (importInProgress) {
                                importInProgress = false // Сбрасываем флаг
                                Toast.makeText(
                                    context,
                                    "Import completed successfully",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        else -> {}
                    }
                }

                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier.height(48.dp), // Уменьшенная высота (стандартная обычно 56dp или больше)
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp // Убираем тень для более плоского вида
                        ) {
                            items.forEachIndexed { index, item ->
                                NavigationBarItem(
                                    icon = {
                                        when (index) {
                                            0 -> Icon(
                                                Icons.Default.MenuBook,
                                                contentDescription = item,
                                                modifier = Modifier.size(20.dp) // Уменьшенный размер иконки
                                            )
                                            1 -> Icon(
                                                Icons.Default.Book,
                                                contentDescription = item,
                                                modifier = Modifier.size(20.dp) // Уменьшенный размер иконки
                                            )
                                        }
                                    },
                                    label = {
                                        Text(
                                            item,
                                            style = MaterialTheme.typography.bodySmall // Уменьшенный текст
                                        )
                                    },
                                    selected = selectedItem == index,
                                    onClick = {
                                        selectedItem = index
                                        when (index) {
                                            0 -> navController.navigate("story") {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                            1 -> navController.navigate("dictionary") {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                )
                            }
                        }
                    }
                ) { paddingValues ->
                    NavHost(
                        navController = navController,
                        startDestination = "story",
                        modifier = Modifier.padding(paddingValues)
                    ) {
                        composable("story") {
                            BakingScreen()
                        }
                        composable("dictionary") {
                            DictionaryScreen(
                                viewModel = viewModel,
                                onImportClick = { filePickerLauncher.launch("text/csv") }
                            )
                        }
                    }
                }
            }
        }
    }
}