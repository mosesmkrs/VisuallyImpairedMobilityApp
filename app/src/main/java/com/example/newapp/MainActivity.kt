package com.example.newapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.composable



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = Routes.homeScreen
            ) {
                composable(Routes.homeScreen) {
                    HomeScreen(navController)
                }
                composable(Routes.profilePage) {
                    ProfilePage(navController)
                }
                composable(Routes.navigationPage) {
                    NavigationPage(navController)
                }
                composable(Routes.alertsPage){
                    AlertsPage(navController)
                }
            }
        }
    }
}

