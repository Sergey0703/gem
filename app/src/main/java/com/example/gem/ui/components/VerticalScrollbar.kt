package com.example.gem.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun VerticalScrollbar(
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
    sentences: List<String> = emptyList(),
    currentSentenceIndex: Int = -1,
    width: Dp = 8.dp,
    thumbColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    // Показываем скроллбар только если есть что прокручивать
    if (scrollState.maxValue > 0) {
        // Получаем размеры и позицию через onGloballyPositioned
        var trackHeight by remember { mutableStateOf(0) }

        Box(
            modifier = modifier
                .width(width)
                .fillMaxHeight()
                .background(trackColor, shape = RoundedCornerShape(4.dp))
                .onGloballyPositioned { coordinates ->
                    // Запоминаем фактическую высоту трека скроллбара в пикселях
                    trackHeight = coordinates.size.height
                }
        ) {
            // Размер ползунка (процент от высоты трека)
            val thumbSizePercent = 0.06f

            // Высота ползунка в пикселях
            val thumbHeightPx = (trackHeight * thumbSizePercent).toInt()

            // Максимальное смещение ползунка в пикселях
            val maxOffsetPx = trackHeight - thumbHeightPx

            // Прогресс прокрутки (0.0 - 1.0)
            val scrollProgress = scrollState.value.toFloat() / scrollState.maxValue.toFloat()

            // Позиция ползунка в пикселях
            val thumbOffsetPx = (scrollProgress * maxOffsetPx).toInt()

            // Логируем значения для отладки
            Log.d("VerticalScrollbar", "trackHeight: $trackHeight")
            Log.d("VerticalScrollbar", "thumbHeightPx: $thumbHeightPx")
            Log.d("VerticalScrollbar", "maxOffsetPx: $maxOffsetPx")
            Log.d("VerticalScrollbar", "scrollProgress: $scrollProgress")
            Log.d("VerticalScrollbar", "thumbOffsetPx: $thumbOffsetPx")

            // Рисуем ползунок с использованием graphicsLayer для более точного позиционирования
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height((thumbHeightPx / LocalDensity.current.density).dp)
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = thumbOffsetPx.toFloat()
                    }
                    .shadow(elevation = 1.dp, shape = RoundedCornerShape(4.dp))
                    .background(thumbColor, RoundedCornerShape(4.dp))
            )

            // Индикатор текущего предложения (если есть)
            if (currentSentenceIndex >= 0 && sentences.isNotEmpty()) {
                val sentenceProgress = if (sentences.size > 1)
                    currentSentenceIndex.toFloat() / (sentences.size - 1).toFloat()
                else
                    0f

                val markerOffsetPx = (sentenceProgress * trackHeight).toInt()

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(6.dp)
                        .graphicsLayer {
                            translationY = markerOffsetPx.toFloat()
                        }
                        .shadow(
                            elevation = 2.dp,
                            shape = RoundedCornerShape(3.dp)
                        )
                        .background(
                            Color.Yellow.copy(alpha = 0.8f),
                            RoundedCornerShape(3.dp)
                        )
                )
            }
        }
    }
}