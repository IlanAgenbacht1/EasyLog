package com.easylog.android.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PathData(
    val path: Path,
    val color: Color = Color.Black,
    val strokeWidth: Float = 8f
)

@Composable
fun SignatureCanvas(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSignatureChange: (Boolean, Bitmap?) -> Unit = { _, _ -> }
) {
    // List of completed strokes
    val completedPaths = remember { mutableStateListOf<PathData>() }

    // The path currently being drawn
    var currentPath by remember { mutableStateOf<Path?>(null) }

    // Track canvas size for bitmap generation
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val scope = rememberCoroutineScope()

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .height(250.dp) // Explicit height ensures it doesn't collapse
            .clip(RoundedCornerShape(12.dp)) // Match border radius
            .background(Color.White)
            .clipToBounds()
            .onGloballyPositioned { coordinates ->
                canvasSize = coordinates.size
            }
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        // awaitEachGesture is more reliable for drawing than detectDragGestures
                        awaitEachGesture {
                            // 1. Wait for the first touch down
                            val down = awaitFirstDown(requireUnconsumed = false)

                            val newPath = Path().apply {
                                moveTo(down.position.x, down.position.y)
                            }
                            currentPath = newPath

                            // 2. Track movements
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break

                                if (change.pressed) {
                                    change.consume()
                                    newPath.lineTo(change.position.x, change.position.y)

                                    // Re-assign to a copy to force real-time redraw
                                    val pathCopy = Path()
                                    pathCopy.addPath(newPath)
                                    currentPath = pathCopy
                                } else {
                                    // Touch lifted
                                    completedPaths.add(PathData(newPath))
                                    currentPath = null

                                    // Generate bitmap snapshot
                                    val snapshotPaths = completedPaths.toList()
                                    val snapshotSize = canvasSize

                                    scope.launch {
                                        if (snapshotSize.width > 0 && snapshotSize.height > 0) {
                                            val bitmap = createBitmapFromPaths(snapshotPaths, snapshotSize)
                                            onSignatureChange(true, bitmap)
                                        }
                                    }
                                    break
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        // Draw completed strokes
        completedPaths.forEach { pathData ->
            drawPath(
                path = pathData.path,
                color = pathData.color,
                style = Stroke(
                    width = pathData.strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        // Draw active stroke
        currentPath?.let { path ->
            drawPath(
                path = path,
                color = Color.Black,
                style = Stroke(
                    width = 8f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

private suspend fun createBitmapFromPaths(paths: List<PathData>, size: IntSize): Bitmap? = withContext(Dispatchers.Default) {
    if (size.width <= 0 || size.height <= 0) return@withContext null

    return@withContext try {
        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // Explicitly paint the bitmap background white
        canvas.drawColor(android.graphics.Color.WHITE)

        val paint = android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            isAntiAlias = true
        }

        paths.forEach { pathData ->
            paint.color = pathData.color.toArgb()
            paint.strokeWidth = pathData.strokeWidth
            canvas.drawPath(pathData.path.asAndroidPath(), paint)
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}