// app/src/main/java/com/example/gem/ui/components/StoryContent.kt

package com.example.gem.ui.components

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.gem.utils.isPunctuation
import com.google.accompanist.flowlayout.FlowRow
import com.google.accompanist.flowlayout.FlowCrossAxisAlignment
import com.google.accompanist.flowlayout.FlowMainAxisAlignment
import com.google.accompanist.flowlayout.SizeMode

@Composable
fun StoryContent(
    showSelectedWords: Boolean,
    sentences: List<String>,
    textForDisplay: String,
    currentSpokenWord: String,
    isSpeaking: Boolean,
    highlightedSentence: String,
    lastHighlightedSentence: String,
    scrollState: androidx.compose.foundation.ScrollState,
    onSentenceClick: (String, Int) -> Unit, // Updated to include sentence index
    onWordLongPress: (String, TextLayoutResult, ((Offset) -> Unit) -> Unit) -> Unit,
    selectedWords: List<String>,
    onWordClick: (String) -> Unit,
    // Added these parameters for direct index-based highlighting
    highlightedSentenceIndex: Int = -1,
    currentSentenceIndex: Int = -1,
    lastHighlightedSentenceIndex: Int = -1
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        AnimatedVisibility(
            visible = !showSelectedWords,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            // Container with text and scrollbar
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Scrollable content
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(end = 12.dp)
                ) {
                    // For sentence splitting and highlighting use version with separators
                    if (sentences.size > 1) {
                        // Sentence splitting successful, display with highlighting
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                mainAxisAlignment = FlowMainAxisAlignment.Start,
                                crossAxisAlignment = FlowCrossAxisAlignment.Start,
                                mainAxisSize = SizeMode.Wrap,
                                crossAxisSpacing = 8.dp,
                                mainAxisSpacing = 8.dp
                            ) {
                                sentences.forEachIndexed { index, sentence ->
                                    // Determine if current sentence should be highlighted - UPDATED LOGIC
                                    val shouldHighlight = when {
                                        // When reading, highlight only current spoken sentence by index
                                        isSpeaking && index == currentSentenceIndex -> true

                                        // When speaking by index match
                                        isSpeaking && currentSpokenWord.isNotEmpty() &&
                                                currentSpokenWord.replace("<<SENTENCE_END>>", "").trim() ==
                                                sentence.replace("<<SENTENCE_END>>", "").trim() -> true

                                        // When reading stopped, highlight by direct index
                                        !isSpeaking && index == highlightedSentenceIndex -> true

                                        // Fallback to content-based comparison if we need to
                                        !isSpeaking && (
                                                (lastHighlightedSentenceIndex == index && index >= 0) ||
                                                        (highlightedSentence.isNotEmpty() &&
                                                                highlightedSentence.replace("<<SENTENCE_END>>", "").trim() ==
                                                                sentence.replace("<<SENTENCE_END>>", "").trim()) ||
                                                        (lastHighlightedSentence.isNotEmpty() &&
                                                                lastHighlightedSentence.replace("<<SENTENCE_END>>", "").trim() ==
                                                                sentence.replace("<<SENTENCE_END>>", "").trim())
                                                ) -> true

                                        else -> false
                                    }

                                    // Log highlight state for debugging
                                    if (shouldHighlight) {
                                        Log.d("StoryContent", "Highlighting sentence $index: ${sentence.take(30)}...")
                                    }

                                    // Display sentence without separators
                                    val cleanSentence = sentence.replace("<<SENTENCE_END>>", "")

                                    // For tracking text layout and specific word highlighting
                                    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                                    Text(
                                        text = cleanSentence + " ",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        overflow = TextOverflow.Visible,
                                        onTextLayout = { textLayoutResult = it },
                                        modifier = Modifier
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onTap = {
                                                        // Handle tap only if not currently reading
                                                        if (!isSpeaking) {
                                                            Log.d("StoryContent", "Tapped on sentence $index: ${cleanSentence.take(30)}...")
                                                            onSentenceClick(sentence, index)
                                                        }
                                                    },
                                                    onLongPress = { offset ->
                                                        try {
                                                            val layoutResult = textLayoutResult
                                                            if (layoutResult != null) {
                                                                // Fix function for handling without suspend
                                                                onWordLongPress(cleanSentence, layoutResult) { handler ->
                                                                    handler(offset)
                                                                }
                                                            }
                                                        } catch (e: Exception) {
                                                            Log.e("StoryContent", "Error in onLongPress: ${e.message}", e)
                                                        }
                                                    }
                                                )
                                            }
                                            .then(
                                                if (shouldHighlight) {
                                                    Modifier.background(Color.Yellow.copy(alpha = 0.3f))
                                                } else {
                                                    Modifier
                                                }
                                            )
                                    )
                                }
                            }
                        }
                    } else {
                        // If splitting failed, display full text
                        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                        Text(
                            text = textForDisplay,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            overflow = TextOverflow.Visible,
                            onTextLayout = { textLayoutResult = it },
                            modifier = Modifier
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            // When no sentences, highlight whole text
                                            if (!isSpeaking) {
                                                onSentenceClick(if (highlightedSentence.isNotEmpty()) "" else textForDisplay, 0)
                                            }
                                        },
                                        onLongPress = { offset ->
                                            try {
                                                val layoutResult = textLayoutResult
                                                if (layoutResult != null) {
                                                    // Fix function for handling without suspend
                                                    onWordLongPress(textForDisplay, layoutResult) { handler ->
                                                        handler(offset)
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e("StoryContent", "Error in onLongPress for full text: ${e.message}", e)
                                            }
                                        }
                                    )
                                }
                                .then(
                                    if (currentSpokenWord.isNotEmpty()) {
                                        Modifier.background(Color.Yellow.copy(alpha = 0.3f))
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                }

                // Improved vertical scrollbar
                VerticalScrollbar(
                    scrollState = scrollState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                    sentences = sentences,
                    currentSentenceIndex = if (sentences.isNotEmpty()) {
                        if (currentSentenceIndex >= 0) currentSentenceIndex else {
                            sentences.indexOfFirst { sentence ->
                                val cleanSentence = sentence.replace("<<SENTENCE_END>>", "").trim()
                                val stateSentence = currentSpokenWord.replace("<<SENTENCE_END>>", "").trim()
                                cleanSentence == stateSentence
                            }
                        }
                    } else -1
                )
            }
        }

        AnimatedVisibility(
            visible = showSelectedWords,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            // Container with word grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 100.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(selectedWords) { word ->
                    Surface(
                        color = if (currentSpokenWord == word)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        else
                            MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .clickable {
                                onWordClick(word)
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