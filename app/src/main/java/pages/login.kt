package pages


import android.app.Application
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import apis.GoogleAuthClient
import com.example.newapp.R
import com.example.newapp.Routes
import com.example.newapp.SQL.PC.pCViewModel
import com.example.newapp.SQL.SC.sCViewModel
import com.example.newapp.SQL.users.UserViewModel
import com.example.newapp.SQL.users.Users
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun GoogleSignInScreen(
    googleAuthClient: GoogleAuthClient,
    lifecycleOwner: LifecycleOwner,
    navController: NavController,
    tts: TextToSpeech
) {
    val context = LocalContext.current
    var isSignIn by remember { mutableStateOf(googleAuthClient.isSingedIn()) }
//    val userId by remember { mutableStateOf(googleAuthClient.getUserId()) }
//    val userName by remember { mutableStateOf(googleAuthClient.getUserName() ?: "Unknown") }
//    val userEmail by remember { mutableStateOf(googleAuthClient.getUserEmail() ?: "No Email") }
    var isLoading by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    var dbSaveSuccess by remember { mutableStateOf(true) }
    
    // Initialize ViewModels for SQLite database operations
    val userViewModel = remember {
        ViewModelProvider(
            lifecycleOwner as ViewModelStoreOwner,
            ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application)
        ).get(UserViewModel::class.java)
    }
    
    // Initialize contact ViewModels to check if contacts exist
    val primaryContactViewModel = remember {
        ViewModelProvider(
            lifecycleOwner as ViewModelStoreOwner,
            ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application)
        ).get(pCViewModel::class.java)
    }
    
    val secondaryContactViewModel = remember {
        ViewModelProvider(
            lifecycleOwner as ViewModelStoreOwner,
            ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application)
        ).get(sCViewModel::class.java)
    }

    // Initialize TTS callback
    val tts = remember {
        TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                ttsReady = true
            }
        }
    }

    // Speak only when TTS is ready
    LaunchedEffect(ttsReady) {
        if (ttsReady) {
            speakText(tts, "Welcome to the app. Your Navigation Assistant! Double tap to sign in")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(top = 28.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { // Detect double tap anywhere on the screen
                        lifecycleOwner.lifecycleScope.launch {
                            isLoading = true
                            speakText(tts, "Signing in with Google. Please wait.")

                            val success = googleAuthClient.signIn()
                            if (success) {
                                val newUserName = googleAuthClient.getUserName() ?: "Unknown"
                                val newUserId = googleAuthClient.getUserId() ?: ""
                                val newUserEmail = googleAuthClient.getUserEmail() ?: ""
                                val newUserPhotoUrl = googleAuthClient.getUserPhotoUrl() ?: ""
                                
                                // Create a new user object for SQLite database
                                val user = Users(
                                    firebaseUUID = newUserId,
                                    name = newUserName,
                                    email = newUserEmail,
                                    photoURL = newUserPhotoUrl
                                )
                                
                                try {
                                    // Check if user already exists and handle accordingly
                                    val userExists = userViewModel.userExists(newUserId)
                                    if (userExists) {
                                        // User exists, update their information
                                        Log.d("SQLiteDB", "User already exists in database, updating: $newUserName")
                                        userViewModel.insertOrUpdate(user)
                                        dbSaveSuccess = true
                                        speakText(tts, "Sign-in successful! Welcome back, $newUserName")
                                    } else {
                                        // New user, insert them
                                        userViewModel.insert(user)
                                        dbSaveSuccess = true
                                        Log.d("SQLiteDB", "New user saved to SQLite database: $newUserName")
                                        speakText(tts, "Sign-in successful! Welcome, $newUserName")
                                    }
                                } catch (e: Exception) {
                                    dbSaveSuccess = false
                                    Log.e("SQLiteDB", "Error saving user to SQLite database: ${e.message}")
                                    speakText(tts, "Sign-in successful! Welcome, $newUserName. Note: Some user data could not be saved locally.")
                                }
                                
                                isSignIn = true
                                
                                // Check if user has any contacts
                                var userID = 0
                                try {
                                    userID = lifecycleOwner.lifecycleScope.run {
                                        userViewModel.getUserIDByFirebaseUUID(newUserId)
                                    }
                                } catch (e: Exception) {
                                    Log.e("Login", "Error getting user ID: ${e.message}")
                                }
                                if (userID > 0) {
                                    val hasPrimaryContact = primaryContactViewModel.getPrimaryContact(userID) != null
                                    val hasSecondaryContact = secondaryContactViewModel.getSecondaryContact(userID) != null
                                    
                                    if (hasPrimaryContact && hasSecondaryContact) {
                                        // User has both contacts, navigate to home page
                                        Log.d("Navigation", "User has contacts, navigating to home page")
                                        speakText(tts, "Welcome back! Taking you to the home page.")
                                        navController.navigate(Routes.homeScreen)
                                    } else {
                                        // User is missing at least one contact, navigate to contact form
                                        Log.d("Navigation", "User missing contacts, navigating to contact form")
                                        speakText(tts, "Please set up your emergency contacts first.")
                                        navController.navigate(Routes.ContactFormScreen)
                                    }
                                } else {
                                    // User ID not found, navigate to contact form
                                    Log.d("Navigation", "User ID not found, navigating to contact form")
                                    speakText(tts, "Please set up your emergency contacts first.")
                                    navController.navigate(Routes.ContactFormScreen)
                                }
                            } else {
                                speakText(tts, "Sign-in failed. Please try again.")
                                isLoading = false
                            }
                        }
                    }
                )

            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.img),
            contentDescription = "App Logo",
            modifier = Modifier
                .width(401.dp)
                .height(448.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Welcome to TembeaNami",
            color = Color.Black,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, // Keep only one instance
            modifier = Modifier.fillMaxWidth()
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
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else if (isSignIn) {
                // Navigation is handled in the sign-in logic
            } else {
                OutlinedButton(onClick ={
                    lifecycleOwner.lifecycleScope.launch {
                        isLoading = true
                        speakText(tts, "Signing in with Google. Please wait.")
                        
                        val success = googleAuthClient.signIn()
                        if (success) {
                            val newUserName = googleAuthClient.getUserName() ?: "Unknown"
                            val newUserId = googleAuthClient.getUserId() ?: ""
                            val newUserEmail = googleAuthClient.getUserEmail() ?: ""
                            val newUserPhotoUrl = googleAuthClient.getUserPhotoUrl() ?: ""
                            
                            // Create a new user object for SQLite database
                            val user = Users(
                                firebaseUUID = newUserId,
                                name = newUserName,
                                email = newUserEmail,
                                photoURL = newUserPhotoUrl
                            )
                            
                            try {
                                // Check if user already exists and handle accordingly
                                val userExists = userViewModel.userExists(newUserId)
                                if (userExists) {
                                    // User exists, update their information
                                    Log.d("SQLiteDB", "User already exists in database, updating: $newUserName")
                                    userViewModel.insertOrUpdate(user)
                                    dbSaveSuccess = true
                                    speakText(tts, "Sign-in successful! Welcome back, $newUserName")
                                } else {
                                    // New user, insert them
                                    userViewModel.insert(user)
                                    dbSaveSuccess = true
                                    Log.d("SQLiteDB", "New user saved to SQLite database: $newUserName")
                                    speakText(tts, "Sign-in successful! Welcome, $newUserName")
                                }
                            } catch (e: Exception) {
                                dbSaveSuccess = false
                                Log.e("SQLiteDB", "Error saving user to SQLite database: ${e.message}")
                                speakText(tts, "Sign-in successful! Welcome, $newUserName. Note: Some user data could not be saved locally.")
                            }
                            
                            isSignIn = true
                            
                            // Check if user has any contacts
                            var userID = 0
                            try {
                                userID = lifecycleOwner.lifecycleScope.run {
                                    userViewModel.getUserIDByFirebaseUUID(newUserId)
                                }
                            } catch (e: Exception) {
                                Log.e("Login", "Error getting user ID: ${e.message}")
                            }
                            
                            if (userID > 0) {
                                val hasPrimaryContact = primaryContactViewModel.getPrimaryContact(userID) != null
                                val hasSecondaryContact = secondaryContactViewModel.getSecondaryContact(userID) != null
                                
                                if (hasPrimaryContact && hasSecondaryContact) {
                                    // User has both contacts, navigate to home page
                                    Log.d("Navigation", "User has contacts, navigating to home page")
                                    speakText(tts, "Welcome back! Taking you to the home page.")
                                    navController.navigate(Routes.homeScreen)
                                } else {
                                    // User is missing at least one contact, navigate to contact form
                                    Log.d("Navigation", "User missing contacts, navigating to contact form")
                                    speakText(tts, "Please set up your emergency contacts to continue.")
                                    navController.navigate(Routes.ContactFormScreen)
                                }
                            } else {
                                // User ID not found, navigate to contact form
                                Log.d("Navigation", "User ID not found, navigating to contact form")
                                speakText(tts, "Please set up your emergency contacts to continue.")
                                navController.navigate(Routes.ContactFormScreen)
                            }
                        } else {
                            speakText(tts, "Sign-in failed. Please try again.")
                            isLoading = false
                        }
                    }
                }) {
                    Text(
                        text = "Sign in with Google",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }


}



// Utility function to handle speaking
fun speakText(tts: TextToSpeech, text: String) {
    if (tts.isSpeaking) {
        tts.stop() // Stop any ongoing speech
    }
    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
}