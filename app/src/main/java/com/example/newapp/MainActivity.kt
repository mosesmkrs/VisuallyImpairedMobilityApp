package com.example.newapp

import APIs.GoogleAuthClient
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.composable
import com.arcgismaps.ApiKey
import com.arcgismaps.ArcGISEnvironment
import pages.AlertsPage
import pages.ContactFormScreen
import pages.GoogleSignInScreen
import pages.HomeScreen
import pages.NavigationPage
import pages.OfflineMap
import pages.ProfilePage
import pages.SecondaryContactForm

class MainActivity : ComponentActivity() {
    private lateinit var googleAuthClient: GoogleAuthClient
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setApiKey()

        setContent {
            val navController = rememberNavController()
            googleAuthClient = GoogleAuthClient(applicationContext)
            NavHost(
                navController = navController,
                startDestination = Routes.GoogleSignInScreen
            ) {
                composable(Routes.homeScreen) {
                    HomeScreen( googleAuthClient = googleAuthClient,
                        lifecycleOwner = this@MainActivity,
                        navController)
                }
                composable(Routes.profilePage) {
                    ProfilePage( googleAuthClient = googleAuthClient,
                        lifecycleOwner = this@MainActivity,
                        navController)
                }
                composable(Routes.navigationPage) {
                    NavigationPage(navController)
                }
                composable(Routes.alertsPage){
                    AlertsPage(navController)
                }
                composable(Routes.offlineMapPage){
                    OfflineMap(navController)
                }
                composable(Routes.GoogleSignInScreen){
                    GoogleSignInScreen(  googleAuthClient = googleAuthClient,
                        lifecycleOwner = this@MainActivity,
                        navController
                    )
                }
                composable(Routes.ContactFormScreen){
                    ContactFormScreen(navController)
                }
                composable(Routes.SecondaryContactForm){
                    SecondaryContactForm(navController)
                }
            }
        }
    }

    private fun setApiKey() {
        val arcgisKey = BuildConfig.API_KEY
        ArcGISEnvironment.apiKey = ApiKey.create(arcgisKey)
    }
}
