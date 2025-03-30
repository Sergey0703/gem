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
import dagger.hilt.android.lifecycle.HiltViewModel

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
                
                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    if (uri != null) {
                        try {
                            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                viewModel.importCsv(inputStream)
                                Toast.makeText(context, "Начинаю импорт файла...", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Ошибка при чтении файла: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "Файл не выбран", Toast.LENGTH_SHORT).show()
                    }
                }

                // Отслеживаем изменения состояния для показа Toast
                val uiState by viewModel.uiState.collectAsState()
                LaunchedEffect(uiState) {
                    when (uiState) {
                        is DictionaryUiState.Error -> {
                            Toast.makeText(
                                context,
                                (uiState as DictionaryUiState.Error).message,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        is DictionaryUiState.Success -> {
                            // Показываем Toast только если это результат импорта
                            if ((uiState as DictionaryUiState.Success).words.isNotEmpty()) {
                                Toast.makeText(
                                    context,
                                    "Импорт успешно завершен",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        else -> {}
                    }
                }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            items.forEachIndexed { index, item ->
                                NavigationBarItem(
                                    icon = {
                                        when (index) {
                                            0 -> Icon(Icons.Default.MenuBook, contentDescription = item)
                                            1 -> Icon(Icons.Default.Book, contentDescription = item)
                                        }
                                    },
                                    label = { Text(item) },
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
                                    }
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
                            StoryScreen()
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