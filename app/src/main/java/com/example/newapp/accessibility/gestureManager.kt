package com.example.newapp.accessibility



import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

fun Modifier.detectGestures(
    onSingleTap: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
    onSwipeUp: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null,
): Modifier {
    return this
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { onSingleTap?.invoke() },
                onDoubleTap = { onDoubleTap?.invoke() },
                onLongPress = { onLongPress?.invoke() }
            )
        }
        .pointerInput(Unit) {
            detectHorizontalDragGestures { change, dragAmount ->
                change.consume()
                if (dragAmount > 50) {
                    onSwipeRight?.invoke()
                } else if (dragAmount < -50) {
                    onSwipeLeft?.invoke()
                }
            }
        }
        .pointerInput(Unit) {
            detectVerticalDragGestures { change, dragAmount ->
                change.consume()
                if (dragAmount > 50) {
                    onSwipeDown?.invoke()
                } else if (dragAmount < -50) {
                    onSwipeUp?.invoke()
                }
            }
        }
}
