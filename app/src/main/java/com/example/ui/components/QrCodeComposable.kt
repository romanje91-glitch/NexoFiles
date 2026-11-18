package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter

fun generateQrMatrix(content: String, size: Int = 256): BitMatrix? {
  if (content.isBlank()) return null
  return runCatching {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
  }.getOrNull()
}

@Composable
fun QrCodeComposable(
  content: String,
  modifier: Modifier = Modifier,
  size: Dp = 180.dp,
  foregroundColor: Color = Color(0xFF08060E),
  backgroundColor: Color = Color(0xFFF3EFFF),
) {
  val matrix = remember(content) { generateQrMatrix(content, 48) }
  val shape = CutCornerShape(8.dp)
  val borderBrush = Brush.linearGradient(
    colors = listOf(
      MaterialTheme.colorScheme.primary,
      MaterialTheme.colorScheme.secondary,
      MaterialTheme.colorScheme.primary,
    )
  )

  Box(
    modifier = modifier
      .size(size)
      .clip(shape)
      .background(backgroundColor)
      .border(1.5.dp, borderBrush, shape)
      .padding(12.dp)
      .semantics { contentDescription = "Código QR para $content" },
    contentAlignment = Alignment.Center,
  ) {
    if (matrix != null) {
      Canvas(modifier = Modifier.size(size - 24.dp)) {
        val w = matrix.width
        val h = matrix.height
        val cellW = this.size.width / w.toFloat()
        val cellH = this.size.height / h.toFloat()
        for (x in 0 until w) {
          for (y in 0 until h) {
            if (matrix[x, y]) {
              drawRect(
                color = foregroundColor,
                topLeft = Offset(x * cellW, y * cellH),
                size = Size(cellW + 0.5f, cellH + 0.5f),
              )
            }
          }
        }
      }
    }
  }
}
