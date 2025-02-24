package com.example.newapp


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController


@Composable
fun Footer(navController: NavController) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 8.dp)
            .height(56.dp)
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FooterItem("Home") { navController.navigate(Routes.homeScreen) }
        FooterItem("Navigate") { navController.navigate(Routes.navigationPage) }
        FooterItem("Alerts") { navController.navigate(Routes.alertsPage) }
        FooterItem("Settings") { navController.navigate(Routes.profilePage) }
    }
}

@Composable
fun FooterItem(label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(text = label, fontSize = 14.sp, color = Color.Black)
    }
}
