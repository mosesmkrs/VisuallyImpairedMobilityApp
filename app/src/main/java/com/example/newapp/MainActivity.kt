package com.example.newapp

import APIs.GoogleAuthClient
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.arcgismaps.ApiKey
import com.arcgismaps.ArcGISEnvironment
import pages.*
import androidx.compose.foundation.layout.*


import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var googleAuthClient: GoogleAuthClient
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Text-to-Speech
        textToSpeech = TextToSpeech(this, this)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        // Initialize Google Auth Client
        googleAuthClient = GoogleAuthClient(applicationContext)

        // Set ArcGIS API Key
        setApiKey()


        setContent {
            val navController = rememberNavController()

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
//                Button(onClick = { speakText("Welcome to the app!") }) {
//                    Text("Speak")
//                }
            }

            NavHost(
                navController = navController,
                startDestination = Routes.homeScreen
                //startDestination = Routes.GoogleSignInScreen
            ) {
                composable(Routes.homeScreen) {
                    HomeScreen(
                        googleAuthClient,
                        lifecycleOwner = this@MainActivity,
                        navController ,
                        textToSpeech
                    )
                }

                composable(Routes.homeScreen) {
                    HomeScreen(
                        googleAuthClient = googleAuthClient,
                        lifecycleOwner = this@MainActivity,
                        navController = navController,
                        textToSpeech
                    )
                }
                composable(Routes.profilePage) {
                    ProfilePage(
                        googleAuthClient = googleAuthClient,
                        lifecycleOwner = this@MainActivity,
                        navController
                    )
                }
                composable(Routes.navigationPage) {
                    NavigationPage(navController)
                }
                composable(Routes.alertsPage) {
                    AlertsPage(navController)
                }
                composable(Routes.offlineMapPage) {
                    OfflineMap(navController)
                }
                composable(Routes.GoogleSignInScreen) {
                    GoogleSignInScreen(
                        googleAuthClient = googleAuthClient,
                        lifecycleOwner = this@MainActivity,
                        navController,
                        textToSpeech
                    )
                }
                composable(Routes.ContactFormScreen) {
                    ContactFormScreen(navController)
                }
                composable(Routes.SecondaryContactForm) {
                    SecondaryContactForm(navController)
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.language = Locale.US

        }
    }




    private fun setApiKey() {
        val arcgisKey = BuildConfig.API_KEY
        ArcGISEnvironment.apiKey = ApiKey.create(arcgisKey)
    }

    override fun onDestroy() {
        textToSpeech.stop()
        textToSpeech.shutdown()
        speechRecognizer.destroy()
        super.onDestroy()
    }
}