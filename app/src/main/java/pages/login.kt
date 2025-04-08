package pages

import android.app.Activity
import android.app.Application
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.NavController
import apis.GoogleAuthClient
import com.example.newapp.R
import com.example.newapp.Routes
import com.example.newapp.SQL.PC.pCViewModel
import com.example.newapp.SQL.SC.sCViewModel
import com.example.newapp.SQL.users.UserViewModel
import com.example.newapp.SQL.users.Users
import kotlinx.coroutines.launch

@Composable
fun GoogleSignInScreen(
    googleAuthClient: GoogleAuthClient,
    lifecycleOwner: LifecycleOwner,
    navController: NavController,
    tts: TextToSpeech
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Check if user is already signed in
    var isSignedIn by remember { mutableStateOf(googleAuthClient.isSingedIn()) }

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

    // Activity result launcher for Google Sign-In
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result: ActivityResult ->
        isLoading = true
        if (result.resultCode == Activity.RESULT_OK) {
            // Handle successful sign-in
            coroutineScope.launch {
                try {
                    val authResult = result.data?.let { googleAuthClient.handleSignInResult(it) }
                    if (authResult != null) {
                        isSignedIn = true
                        processSignedInUser(googleAuthClient, userViewModel, primaryContactViewModel, secondaryContactViewModel, navController, tts)
                    } else {
                        errorMessage = "Failed to authenticate with Firebase"
                        speakText(tts, "Sign-in failed. Please try again.")
                        isLoading = false
                    }
                } catch (e: Exception) {
                    Log.e("Login", "Error processing sign-in result: ${e.message}")
                    errorMessage = "Error: ${e.message}"
                    speakText(tts, "Sign-in failed. Please try again.")
                    isLoading = false
                }
            }
        } else {
            // User canceled or sign-in failed
            Log.d("Login", "Sign-in canceled or failed: ${result.resultCode}")
            errorMessage = "Sign-in canceled"
            speakText(tts, "Sign-in was canceled. Please try again.")
            isLoading = false
        }
    }

    // Check if already signed in and process the user
    LaunchedEffect(Unit) {
        if (googleAuthClient.isSingedIn()) {
            isLoading = true
            try {
                processSignedInUser(googleAuthClient, userViewModel, primaryContactViewModel, secondaryContactViewModel, navController, tts)
            } catch (e: Exception) {
                Log.e("Login", "Error processing signed-in user: ${e.message}")
                isLoading = false
            }
        } else {
            // Speak welcome message for new users
            speakText(tts, "Welcome to the app. Your Navigation Assistant! Double tap to sign in")
        }
    }

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
                .fillMaxWidth()
                .aspectRatio(1f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Welcome to TembeaNami",
            color = Color.Black,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Your navigation assistant!",
            color = Color.Gray,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )

        // Show error message if any
        errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = it,
                color = Color.Red,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        Box(
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else if (!isSignedIn) {
                OutlinedButton(onClick = {
                    coroutineScope.launch {
                        try {
                            isLoading = true
                            errorMessage = null
                            speakText(tts, "Signing in with Google. Please wait.")

                            // Start the sign-in flow
                            val intentSender = googleAuthClient.signIn()
                            if (intentSender != null) {
                                val intentSenderRequest = IntentSenderRequest.Builder(intentSender).build()
                                signInLauncher.launch(intentSenderRequest)
                                // Loading state will be updated in the activity result callback
                            } else {
                                // No intent sender returned, could be already signed in or error
                                if (googleAuthClient.isSingedIn()) {
                                    isSignedIn = true
                                    processSignedInUser(googleAuthClient, userViewModel, primaryContactViewModel, secondaryContactViewModel, navController, tts)
                                } else {
                                    errorMessage = "Could not start sign-in process"
                                    speakText(tts, "Could not start sign-in process. Please try again.")
                                    isLoading = false
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("Login", "Error initiating sign-in: ${e.message}")
                            errorMessage = "Error: ${e.message}"
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



// Helper function to speak text using TextToSpeech
fun speakText(tts: TextToSpeech, text: String) {
    if (tts.isSpeaking) {
        tts.stop()
    }
    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
}

// Helper function to process a signed-in user
suspend fun processSignedInUser(
    googleAuthClient: GoogleAuthClient,
    userViewModel: UserViewModel,
    primaryContactViewModel: pCViewModel,
    secondaryContactViewModel: sCViewModel,
    navController: NavController,
    tts: TextToSpeech
) {
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
            speakText(tts, "Sign-in successful! Welcome back, $newUserName")
        } else {
            // New user, insert them
            userViewModel.insert(user)
            Log.d("SQLiteDB", "New user saved to SQLite database: $newUserName")
            speakText(tts, "Sign-in successful! Welcome, $newUserName")
        }
    } catch (e: Exception) {
        Log.e("SQLiteDB", "Error saving user to SQLite database: ${e.message}")
        e.printStackTrace()
        speakText(tts, "Sign-in successful! Welcome, $newUserName. Note: Some user data could not be saved locally.")
    }

    // Check if user has any contacts
    var userID = 0
    try {
        userID = userViewModel.getUserIDByFirebaseUUID(newUserId)
        Log.d("Login", "Retrieved user ID: $userID for Firebase UID: $newUserId")
    } catch (e: Exception) {
        Log.e("Login", "Error getting user ID: ${e.message}")
        e.printStackTrace()
    }

    if (userID > 0) {
        val hasPrimaryContact = primaryContactViewModel.getPrimaryContact(userID) != null
        val hasSecondaryContact = secondaryContactViewModel.getSecondaryContact(userID) != null

        Log.d("Login", "Contact check - Primary: $hasPrimaryContact, Secondary: $hasSecondaryContact")

        if (hasPrimaryContact && hasSecondaryContact) {
            // User has both contacts, navigate to home page
            Log.d("Navigation", "User has both contacts, navigating to home page")
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
        Log.d("Navigation", "User ID not found or error occurred, navigating to contact form")
        speakText(tts, "Please set up your emergency contacts first.")
        navController.navigate(Routes.ContactFormScreen)
    }
}