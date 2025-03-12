package com.example.newapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import kotlinx.coroutines.launch

import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign


@Composable
fun GoogleSignInScreen(
    googleAuthClient: GoogleAuthClient,
    lifecycleOwner: LifecycleOwner,
    navController: NavController
) {
    var isSignIn by remember { mutableStateOf(googleAuthClient.isSingedIn()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.img),
            contentDescription = "App Logo",
            modifier = Modifier
                .width(401.dp)
                .height(448.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Welcome to TembeaNami",
            color = Color.Black,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Your navigation assistant!",
            color = Color.Gray,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(52.dp))

        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            if (isSignIn) {
                navController.navigate(Routes.ContactFormScreen)
//                OutlinedButton(onClick = {
//                    lifecycleOwner.lifecycleScope.launch {
//                        googleAuthClient.signOut()
//                        isSignIn = false
//                    }
//                    navController.navigate(Routes.GoogleSignInScreen)
//                }) {
//                    Text(
//                        text = "Sign Out",
//                        fontSize = 16.sp,
//                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
//                    )
//                }
            } else {
                OutlinedButton(onClick = {
                    lifecycleOwner.lifecycleScope.launch {
                        isSignIn = googleAuthClient.signIn()
                    }
                }) {
                    Text(
                        text = "Sign In With Google",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
