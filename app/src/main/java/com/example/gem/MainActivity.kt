package com.example.gem

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.gem.ui.theme.GemTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GemTheme {
                val navController = rememberNavController()
                var selectedItem by remember { mutableStateOf(0) }
                val items = listOf("Story", "Dictionary")

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
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "story",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("story") {
                            StoryScreen()
                        }
                        composable("dictionary") {
                            DictionaryScreen()
                        }
                    }
                }
            }
        }
    }
}