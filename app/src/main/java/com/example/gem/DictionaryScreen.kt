package com.example.gem

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DictionaryScreen(
    viewModel: DictionaryViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { inputStream ->
                viewModel.importCsv(context, inputStream)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(
            onClick = { launcher.launch("text/csv") }
        ) {
            Text("Import CSV File")
        }

        when (uiState) {
            is DictionaryUiState.Initial -> {
                Text("Click the button above to import a CSV file")
            }
            is DictionaryUiState.Loading -> {
                CircularProgressIndicator()
            }
            is DictionaryUiState.Success -> {
                Text("Successfully imported ${(uiState as DictionaryUiState.Success).importedCount} words")
            }
            is DictionaryUiState.Error -> {
                Text(
                    text = (uiState as DictionaryUiState.Error).message,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
} 