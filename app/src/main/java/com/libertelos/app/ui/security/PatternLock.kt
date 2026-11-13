package com.libertelos.app.ui.security

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.libertelos.app.ui.theme.InkTeal
import kotlin.math.hypot
import kotlin.math.min

@Composable
fun PatternLock(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    lineColor: Color = InkTeal,
    onPatternComplete: (List<Int>) -> Unit
) {
    val selected = remember { mutableStateListOf<Int>() }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val hitRadius = 48f

    fun centersFor(size: IntSize): List<Offset> {
        if (size.width == 0 || size.height == 0) return emptyList()
        val dim = min(size.width, size.height).toFloat()
        val gap = dim / 4f
        val start = (dim - gap * 2f) / 2f
        return (0 until 9).map { i ->
            val row = i / 3
            val col = i % 3
            Offset(start + col * gap, start + row * gap)
        }
    }

    fun hitTest(pos: Offset, centers: List<Offset>): Int? {
        centers.forEachIndexed { index, center ->
            if (hypot(pos.x - center.x, pos.y - center.y) <= hitRadius) return index
        }
        return null
    }

    val centers = centersFor(canvasSize)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(24.dp)
            .onSizeChanged { canvasSize = it }
            .pointerInput(enabled, canvasSize) {
                if (!enabled) return@pointerInput
                val pts = centersFor(canvasSize)
                detectDragGestures(
                    onDragStart = { offset ->
                        selected.clear()
                        finger = offset
                        hitTest(offset, pts)?.let { selected.add(it) }
                    },
                    onDrag = { change, _ ->
                        val pos = change.position
                        finger = pos
                        hitTest(pos, pts)?.let { id ->
                            if (id !in selected) selected.add(id)
                        }
                    },
                    onDragEnd = {
                        finger = null
                        if (selected.isNotEmpty()) {
                            onPatternComplete(selected.toList())
                        }
                        selected.clear()
                    },
                    onDragCancel = {
                        finger = null
                        selected.clear()
                    }
                )
            }
    ) {
        val dotColor = Color(0xFF8A94A6)
        val active = lineColor

        if (selected.size >= 2) {
            for (i in 0 until selected.lastIndex) {
                drawLine(
                    color = active,
                    start = centers[selected[i]],
                    end = centers[selected[i + 1]],
                    strokeWidth = 8f,
                    cap = StrokeCap.Round
                )
            }
        }
        val last = selected.lastOrNull()
        val tip = finger
        if (last != null && tip != null && centers.isNotEmpty()) {
            drawLine(
                color = active.copy(alpha = 0.55f),
                start = centers[last],
                end = tip,
                strokeWidth = 8f,
                cap = StrokeCap.Round
            )
        }

        centers.forEachIndexed { index, center ->
            val on = index in selected
            drawCircle(
                color = if (on) active else dotColor,
                radius = if (on) 22f else 18f,
                center = center
            )
            if (on) {
                drawCircle(
                    color = Color.White,
                    radius = 8f,
                    center = center
                )
            }
        }
    }
}

/** 至少连接 4 个点才视为有效手势 */
fun isValidPattern(dots: List<Int>): Boolean = dots.size >= 4
