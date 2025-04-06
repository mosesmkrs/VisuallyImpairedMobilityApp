package pages

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
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

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.ui.text.style.TextOverflow
import android.app.Application
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.newapp.SQL.PC.PrimaryContact
import com.example.newapp.SQL.PC.pCViewModel
import com.example.newapp.SQL.SC.SecondaryContact
import com.example.newapp.SQL.SC.sCViewModel
import com.example.newapp.SQL.users.UserViewModel

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

    // Initialize ViewModels
    val userViewModel = remember {
        ViewModelProvider(
            context as ViewModelStoreOwner,
            ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application)
        ).get(UserViewModel::class.java)
    }
    
    val primaryContactViewModel = remember {
        ViewModelProvider(
            context as ViewModelStoreOwner,
            ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application)
        ).get(pCViewModel::class.java)
    }
    
    val secondaryContactViewModel = remember {
        ViewModelProvider(
            context as ViewModelStoreOwner,
            ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application)
        ).get(sCViewModel::class.java)
    }
    
    // Get current user ID and contacts
    val currentFirebaseUUID = googleAuthClient.getUserId() ?: ""
    var currentUserID by remember { mutableStateOf(0) }
    var primaryContact by remember { mutableStateOf<PrimaryContact?>(null) }
    var secondaryContact by remember { mutableStateOf<SecondaryContact?>(null) }
    
    // Load user and contacts from SQLite
    LaunchedEffect(currentFirebaseUUID) {
        if (currentFirebaseUUID.isNotEmpty()) {
            val user = userViewModel.getUserByFirebaseUUID(currentFirebaseUUID)
            if (user != null) {
                currentUserID = user.userID
                Log.d("ProfilePage", "Loaded user ID: $currentUserID")
                
                // Load contacts
                primaryContact = primaryContactViewModel.getPrimaryContact(currentUserID)
                secondaryContact = secondaryContactViewModel.getSecondaryContact(currentUserID)
                
                Log.d("ProfilePage", "Primary contact: ${primaryContact?.contactname ?: "None"}")
                Log.d("ProfilePage", "Secondary contact: ${secondaryContact?.contactname ?: "None"}")
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

            EmergencyContactCard(
                type = "Primary Contact",
                name = primaryContact?.contactname ?: "Add Primary Contact",
                phone = primaryContact?.contactnumber
            ) {
                navController.navigate(Routes.ContactFormScreen)
            }
            
            EmergencyContactCard(
                type = "Secondary Contact",
                name = secondaryContact?.contactname ?: "Add Secondary Contact",
                phone = secondaryContact?.contactnumber
            ) {
                navController.navigate(Routes.SecondaryContactForm)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            // Settings
            Text(text = "Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))

            SettingItem("Alert Settings", R.drawable.alert_icon)
            SettingItem("Audio Settings", R.drawable.audio_icon)
            SettingItem("Security & Privacy", R.drawable.shield_icon)
            
            // Database Viewer Button
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
fun EmergencyContactCard(name: String, type: String, phone: String? = null, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFEFEF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = type, fontSize = 15.sp, color = Color.DarkGray, fontWeight = FontWeight.Bold)
                Text(text = name,fontSize = 12.sp )
                if (!phone.isNullOrEmpty()) {
                    Text(text = phone, fontSize = 12.sp, color = Color.Gray)
                }
            }
            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit")
        }
    }
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



