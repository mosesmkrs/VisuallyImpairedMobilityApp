package com.example.newapp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationPage(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Top Navigation Bar
        TopAppBar(
            title = { Text("Navigate", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    R.drawable.arrow_forward_icon
                }
            },
            actions = {
                Row {
                    IconButton(onClick = { /* Handle map action */ }) {
                        R.drawable.arrow_forward_icon
                    }
                    IconButton(onClick = { /* Handle history action */ }) {
                        R.drawable.arrow_forward_icon
                    }
                    IconButton(onClick = { /* Handle bookmarks action */ }) {
                        R.drawable.arrow_forward_icon
                    }
                    TextButton(onClick = { /* Handle cancel */ }) {
                        Text("Cancel", color = Color.Black, fontSize = 16.sp)
                    }
                }
            },
//            backgroundColor = Color.White,
//            elevation = 0.dp
        )

        // Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(Color.White, shape = RoundedCornerShape(12.dp))
                .border(1.dp, Color.LightGray, shape = RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                R.drawable.arrow_forward_icon
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Where to?",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
        }

        // Map Section
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.emergency_icon), // Replace with your map image
                contentDescription = "Map",
                modifier = Modifier.fillMaxSize()
            )

        }
    }
}
