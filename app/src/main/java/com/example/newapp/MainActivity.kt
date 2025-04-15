package com.example.newapp

import android.content.Context
import apis.GoogleAuthClient
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.arcgismaps.ApiKey
import com.arcgismaps.ArcGISEnvironment
import com.example.newapp.SQL.users.UserViewModel
import pages.AlertsPage
import pages.ContactFormScreen
import pages.DatabaseViewerScreen
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

            // Create a UserViewModel to access the database
            val userViewModel = ViewModelProvider(
                this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            ).get(UserViewModel::class.java)

            // State to hold the userId
            var userId by remember { mutableStateOf(-1) }

            // Get the Firebase UID from GoogleAuthClient
            val firebaseUid = googleAuthClient.getUserId()

            // Effect to retrieve the userId when Firebase UID is available
            LaunchedEffect(firebaseUid) {
                if (!firebaseUid.isNullOrEmpty()) {
                    // Get the userId from the database using the Firebase UID
                    val retrievedUserId = userViewModel.getUserIDByFirebaseUUID(firebaseUid)
                    userId = retrievedUserId

                    // Log the retrieved userId for debugging
                    android.util.Log.d("MainActivity", "Retrieved userId: $userId for Firebase UID: $firebaseUid")

                    // Save the userId to SharedPreferences for future use
                    val sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                    sharedPreferences.edit().putInt("userId", userId).apply()
                } else {
                    android.util.Log.e("MainActivity", "Firebase UID is null or empty")
                }
            }

            NavHost(

                navController = navController,
                startDestination = Routes.GoogleSignInScreen
            ) {
                composable(Routes.homeScreen) {
                    HomeScreen(
                        googleAuthClient,
                        lifecycleOwner = this@MainActivity,
                        navController,
                        textToSpeech,
                        userId = userId
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
                composable(Routes.DatabaseViewerScreen){
                    DatabaseViewerScreen(navController, textToSpeech)
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