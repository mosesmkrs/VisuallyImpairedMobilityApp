package com.example.newapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@Composable
fun ProfilePage(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA)) // Light Gray Background
            .padding(16.dp)
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(),
            contentAlignment = Alignment.Center // ✅ Centers text horizontally
        ) {
            Text(
                text = "Profile",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Profile Header
        ProfileHeader()

        Spacer(modifier = Modifier.height(16.dp))

        // Emergency Contacts Section
        SectionTitle("Emergency Contacts")

        ContactCard(name = "Jane Doe", relation = "Primary Contact")
        ContactCard(name = "Jane Doe", relation = "Secondary Contact")

        Spacer(modifier = Modifier.height(16.dp))

        // Settings Section
        SectionTitle("Settings")

        SettingItem(label = "Alert Settings")
        SettingItem(label = "Audio Settings")
        SettingItem(label = "Security & Privacy")

        Spacer(modifier = Modifier.weight(1f))
        Footer(navController)
    }
}

@Composable
fun ProfileHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "John Doe",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "ID: 52417",
            fontSize = 16.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun ContactCard(name: String, relation: String) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(text = relation, fontSize = 14.sp, color = Color.Gray)
        }
    }
}

@Composable
fun SettingItem(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, shape = RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
