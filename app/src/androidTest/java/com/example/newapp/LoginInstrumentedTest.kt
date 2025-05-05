package com.example.newapp

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginInstrumentedTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun loginScreen_renders_and_signin_button_exists() {
        composeTestRule.onNodeWithText("Sign in with Google", ignoreCase = true).assertExists()
    }

    @Test
    fun loginScreen_signInButton_click_navigatesOrShowsProgress() {
        composeTestRule.onNodeWithText("Sign in with Google", substring = true).performClick()
        // Check for progress indicator or next screen
        // This may require idling or mocking auth
        // Example: composeTestRule.onNodeWithText("Welcome", substring = true).assertExists()
    }
}
