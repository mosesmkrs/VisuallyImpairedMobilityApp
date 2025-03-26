package com.example.newapp.accessibility

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.applyGestures(onDoubleTap: () -> Unit, onSwipe: () -> Unit): Modifier {
    return this
        .pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { onDoubleTap() }) // Double tap triggers action
        }
        .pointerInput(Unit) {
            detectHorizontalDragGestures { _, _ -> onSwipe() } // Swipe triggers action
        }
}
