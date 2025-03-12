package com.example.newapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.composable
import com.arcgismaps.ApiKey
import com.arcgismaps.ArcGISEnvironment

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
                    HomeScreen(navController)
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
            }
        }
    }

    private fun setApiKey() {
        ArcGISEnvironment.apiKey = ApiKey.create("AAPTxy8BH1VEsoebNVZXo8HurGu9cQcYKiJgHHRM-fKgLmr64fiBDAtblVFYCJD0E6grZzMq1xQp7o_RiBLr-ANCdYXdRK_Wvc1pk2-lxvUzrSLAXEN3S6uZpw4y-43cxdKq4GG6gheC7XgJ0dfDPCEZOb5zWprokyXd9YUkKJzKU5O-rgv6yT5H2cD5lLieYddCiVtQr_15ws1VUA-F4ZZUjkHfJT6cePd7OPn5ltgIvNo.AT1_7QiQK66R")
    }
}
