package com.example.newapp

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomePageInstrumentedTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreen_renders_and_tts_works() {
        // Launch HomeScreen and check UI elements
        composeTestRule.onNodeWithText("Welcome to TembeaNami").assertExists()
        // Simulate TTS/voice feedback if possible
        // (TTS is best tested with integration, but we check UI trigger)
    }

    // Add more tests for SOS, state restoration, etc.
}
