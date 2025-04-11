package pages

import android.speech.tts.TextToSpeech
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import components.Footer
import apis.GoogleAuthClient
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
    var tts = remember { TextToSpeech(context) { } }


    // Initialize TTS
    tts = remember{
        TextToSpeech(context){
                status ->
            if(status == TextToSpeech.SUCCESS){
                tts.language = Locale.getDefault()
                tts.speak("You are on the profile page", TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }
    // Cleanup TTS when the screen is removed
    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
    ) {
        // Profile Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(vertical = 12.dp)
        ) {
            // Centered Profile Text
            Text(
                text = "Profile",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.align(Alignment.Center)
            )

//            // Settings Icon aligned to the right
//            Image(
//                painter = painterResource(id = R.drawable.settings_icon),
//                contentDescription = "Settings",
//                modifier = Modifier
//                    .size(26.dp)
//                    .align(Alignment.CenterEnd)
//            )
        }


        Spacer(modifier = Modifier.height(16.dp))

        // User Info
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 24.dp)
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
            Column(modifier = Modifier.weight(1f)) {
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
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logout),
                            contentDescription = "Logout",
                            modifier = Modifier.size(26.dp)
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

            EmergencyContactCard("Jane Doe", "Primary Contact") {
                navController.navigate(Routes.ContactFormScreen)
            }
            EmergencyContactCard("Jane Doe", "Secondary Contact") {
                navController.navigate(Routes.SecondaryContactForm)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            // Settings
//            Text(text = "Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp)
//            Spacer(modifier = Modifier.height(8.dp))
//
//            SettingItem("Alert Settings", R.drawable.alert_icon)
//            SettingItem("Audio Settings", R.drawable.audio_icon)
//            SettingItem("Security & Privacy", R.drawable.shield_icon)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEFEFEF), shape = RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { navController.navigate(Routes.DatabaseViewerScreen) }
                    .padding(12.dp)
                    .semantics { contentDescription = "Database Viewer. View SQLite database tables." },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.settings_icon), // Using settings icon as placeholder
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Database Viewer",
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    painter = painterResource(id = R.drawable.arrow_forward_icon),
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }



        Spacer(modifier = Modifier.weight(1f))
        Footer(navController)
    }
}

@Composable
fun EmergencyContactCard(name: String, type: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFEFEFEF), shape = RoundedCornerShape(12.dp)) // Rounded corners
            .clip(RoundedCornerShape(12.dp))
            .padding(12.dp)
            .clickable{ onClick() },
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



