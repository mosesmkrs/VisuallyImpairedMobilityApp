package com.example.newapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@Composable
fun NavigationPage(navController: NavController) {
    Scaffold(
        topBar = { NavigationTopBar(navController) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8F9FA)) // Light gray background
                .padding(paddingValues)
                .statusBarsPadding()
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Search Bar
            SearchBar()

            Spacer(modifier = Modifier.height(16.dp))

            // Map Display (Placeholder for now)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.LightGray) // Placeholder for the map
            ) {
                Text(
                    text = "Map Placeholder",
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationTopBar(navController: NavController) {
    TopAppBar(
        title = {
            Text(
                text = "Navigate",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) {
                Text("←") // Back button
            }
        },
        actions = {
            Row {
                IconButton(onClick = { /* Handle Map */ }) {
                    Text("🗺️") // Map icon
                }
                IconButton(onClick = { /* Handle Time */ }) {
                    Text("⏳") // Time icon
                }
                IconButton(onClick = { /* Handle Bookmark */ }) {
                    Text("🔖") // Bookmark icon
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { /* Handle Cancel */ }) {
                    Text("Cancel", fontSize = 16.sp)
                }
            }
        }
    )
}

@Composable
fun SearchBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(12.dp)
    ) {
        Text("Where to?", fontSize = 16.sp, color = Color.Gray)
    }
}
