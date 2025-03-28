package com.example.newapp

import apis.GoogleAuthClient
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.arcgismaps.ApiKey
import com.arcgismaps.ArcGISEnvironment
import pages.AlertsPage
import pages.ContactFormScreen
import pages.GoogleSignInScreen
import pages.HomeScreen
import pages.MatatuPage
import pages.NavigationPage
import pages.OfflineMap
import pages.ProfilePage
import pages.SecondaryContactForm
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

        // Set ArcGIS API Key
        setApiKey()


        setContent {
            val navController = rememberNavController()
            googleAuthClient = GoogleAuthClient(applicationContext)

            NavHost(
                navController = navController,
                //startDestination = Routes.homeScreen
               startDestination = Routes.GoogleSignInScreen
            ) {
                composable(Routes.homeScreen) {
                    HomeScreen(
                        googleAuthClient,
                        lifecycleOwner = this@MainActivity,
                        navController,
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
                composable(Routes.MatatuPage){
                    MatatuPage(navController)
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
}

