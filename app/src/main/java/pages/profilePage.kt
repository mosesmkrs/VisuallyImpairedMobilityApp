package pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.navigation.NavController

import coil.compose.AsyncImage
import androidx.compose.material3.*
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import components.Footer
import APIs.GoogleAuthClient
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.newapp.R
import com.example.newapp.Routes
import kotlinx.coroutines.launch
import java.util.Locale


@Composable
fun ProfilePage(googleAuthClient: GoogleAuthClient,
                lifecycleOwner: LifecycleOwner,
                navController: NavController
) {
    var isSignIn by remember { mutableStateOf(googleAuthClient.isSingedIn()) }
    var userName by remember { mutableStateOf(googleAuthClient.getUserName() ?: "Unknown") }
    var userEmail by remember { mutableStateOf(googleAuthClient.getUserEmail() ?: "No Email") }
    var userPhoto by remember { mutableStateOf(googleAuthClient.getUserPhotoUrl()) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }


    // Text-to-Speech setup
    tts = remember {
        TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.speak("Profile Page. Swipe right to sign out. Swipe left to edit emergency contacts.Double tap to repeat the message"
                       , TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }




    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        // Repeat TTS message on double-tap
                        tts?.speak(
                            "Profile Page. Swipe right to sign out. Swipe left to edit your emergency contact. Double-tap to repeat this message.",
                            TextToSpeech.QUEUE_FLUSH, null, null
                        )
                    }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    when {
                        dragAmount > 100 -> showLogoutDialog = true // Swipe Right to Logout
                        dragAmount < -100 -> navController.navigate("ContactFormScreen") // Swipe Left to Edit Contact
                    }
                }
            }
    ) {
        // Profile Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(vertical = 12.dp)
                .semantics { contentDescription = "Profile Page" }
        ) {

            // Centered Profile Text
            Text(
                text = "Profile",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.align(Alignment.Center)
            )

            // Settings Icon aligned to the right
            Image(
                painter = painterResource(id = R.drawable.settings_icon),
                contentDescription = "Settings",
                modifier = Modifier
                    .size(26.dp)
                    .align(Alignment.CenterEnd)
            )
        }


        Spacer(modifier = Modifier.height(16.dp))

        // User Info
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 24.dp)
                .semantics {
                    contentDescription = "User Profile. Name: $userName, Email: $userEmail"
                }
        ) {
            if (userPhoto != null) {
                AsyncImage(
                    model = userPhoto,
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.person_icon),
                    contentDescription = "Default Profile Picture",
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Fit
                )
            }
            Spacer(modifier = Modifier.width(6.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics{
                        contentDescription =  "UserName: $userName, Email: $userEmail"
                    }

            ) {
                Text(text = userName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(text = userEmail, fontSize = 14.sp, color = Color.Gray)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Box(
                contentAlignment = Alignment.Center
            ) {
                if (isSignIn) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clickable {
                                showLogoutDialog = true
                            }
                            .semantics { contentDescription = "Logout Button, Double-tap to sign out" },

                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logout),
                            contentDescription = "Logout. Double tap to sign out.",
                            modifier = Modifier
                                .size(26.dp)
                                .clickable {
                                    showLogoutDialog = true
                                }
                        )
                    }
                }
                else {
                    navController.navigate(Routes.GoogleSignInScreen)
                }
            }
        }

        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false }, // Dismiss dialog on outside tap
                confirmButton = {
                    TextButton(onClick = {
                        lifecycleOwner.lifecycleScope.launch {
                            googleAuthClient.signOut()
                            isSignIn = false
                            isSignIn = false
                            userName = "Unknown"
                            userEmail = "No Email"
                            userPhoto = null
                            showLogoutDialog = false
                            navController.navigate(Routes.GoogleSignInScreen) // Navigate to login screen
                        }
                    }) {
                        Text("Logout", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("Cancel")
                    }
                },
                title = { Text("Confirm Logout") },
                text = { Text("Are you sure you want to logout?") }
            )
        }




        Spacer(modifier = Modifier.height(24.dp))

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            // Emergency Contacts
            Text( text = "Emergency Contacts", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))

            EmergencyContactCard("Jane Doe", "Primary Contact")
            EmergencyContactCard("Jane Doe", "Secondary Contact")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            // Settings
            Text(text = "Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))

            SettingItem("Alert Settings", R.drawable.alert_icon)
            SettingItem("Audio Settings", R.drawable.audio_icon)
            SettingItem("Security & Privacy", R.drawable.shield_icon)
        }


        Spacer(modifier = Modifier.weight(1f))
        Footer(navController)
    }
}

@Composable
fun EmergencyContactCard(name: String, type: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFEFEFEF), shape = RoundedCornerShape(12.dp)) // Rounded corners
            .clip(RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = R.drawable.person_icon),
            contentDescription = "Contact Icon",
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(text = type, fontSize = 14.sp, color = Color.Gray)
        }
        Image(
            painter = painterResource(id = R.drawable.phone_icon),
            contentDescription = "Call Icon",
            modifier = Modifier.size(24.dp)
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun SettingItem(title: String, iconRes: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = title,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Image(
            painter = painterResource(id = R.drawable.arrow_forward_icon),
            contentDescription = "Arrow",
            modifier = Modifier.size(16.dp)
        )
    }
}



