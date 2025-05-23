package com.example.gem.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gem.UiState

@Composable
fun StoryHeader(
    state: UiState.Success,
    onGenerateClick: () -> Unit,
    onLanguageToggleClick: () -> Unit
) {
    // Состояние для диалога подтверждения
    var showConfirmDialog by remember { mutableStateOf(false) }

    // Диалог подтверждения для генерации новой истории
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Generate New Story") },
            text = { Text("This will replace your current story. Are you sure you want to continue?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        onGenerateClick()
                    }
                ) {
                    Text("Generate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Левая часть - информация о тексте
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Информация о длине и времени
                Text(
                    text = "Length: ${if (state.isRussian) state.russianDisplayVersion.length else state.englishDisplayVersion.length} | Time: ${String.format("%.1f", state.generationTime)}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Информация о количестве слов
                Text(
                    text = "Words: ${state.selectedWords.size}/${state.selectedWords.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Правая часть - кнопки управления (в обратном порядке с отступом)
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Кнопка Generate (теперь слева)
                Button(
                    onClick = {
                        // Если текст уже сгенерирован (есть содержимое), показываем диалог подтверждения
                        if (state.englishVersion.isNotEmpty()) {
                            showConfirmDialog = true
                        } else {
                            onGenerateClick()
                        }
                    },
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "Generate",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Отступ между кнопками
                Spacer(modifier = Modifier.width(16.dp))

                // Кнопка переключения языка (теперь справа)
                IconButton(
                    onClick = onLanguageToggleClick,
                    enabled = !state.isTranslating,
                    modifier = Modifier.size(36.dp)
                ) {
                    if (state.isTranslating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Language,
                            contentDescription = "Toggle language"
                        )
                    }
                }
            }
        }
    }
}