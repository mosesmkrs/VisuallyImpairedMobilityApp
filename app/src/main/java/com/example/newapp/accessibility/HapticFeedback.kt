package com.example.newapp.accessibility

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun HapticFeedbackScreen() {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        HapticButton("Long Press Haptic") {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        HapticButton("Text Handle Move") {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
//        HapticButton("Keyboard Tap") {
//            haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
//        }
//        HapticButton("Confirm Haptic") {
//            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
//        }
    }
}

@Composable
fun HapticButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text(text)
    }
}

@Preview
@Composable
fun PreviewHapticScreen() {
    HapticFeedbackScreen()
}
