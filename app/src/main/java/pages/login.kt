package pages


import APIs.GoogleAuthClient
import APIs.UserApiClient
import APIs.UserRequest
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import com.example.newapp.R
import com.example.newapp.Routes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

@Composable
fun GoogleSignInScreen(
    googleAuthClient: GoogleAuthClient,
    lifecycleOwner: LifecycleOwner,
    navController: NavController,
    tts: TextToSpeech // Receive TTS instance
) {
    val context = LocalContext.current
    var isSignedIn by remember { mutableStateOf(googleAuthClient.isSingedIn()) }
    var isLoading by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }


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
            speakText(tts, "Welcome to TembeaNami. Please sign in to continue. Double tap to activate.")
        }
    }




    fun registerUser() {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser != null) {
            val newUser = UserRequest(
                firebaseuid = firebaseUser.uid,
                username = firebaseUser.displayName ?: "Unknown",
                email = firebaseUser.email ?: "No Email"
            )

            val database = FirebaseDatabase.getInstance().getReference("users")
            val userRef = database.child(firebaseUser.uid)

            isLoading = true
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    isLoading = false
                    if (snapshot.exists()) {
                        speakText(tts, "Welcome back,${newUser.username}!")
                        navController.navigate(Routes.ContactFormScreen)
                    } else {
                        speakText(tts, "Creating your account. Please wait.")
                        userRef.setValue(newUser).addOnSuccessListener {
                            UserApiClient.api.createUser(newUser).enqueue(object : Callback<Void> {
                                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                                    isLoading = false
                                    if (response.isSuccessful) {
                                        speakText(tts, "Account created successfully!")
                                        navController.navigate(Routes.SecondaryContactForm)
                                    } else {
                                        val errorBody = response.errorBody()?.string()
                                        speakText(tts, "Failed to register. Please try again.")
                                    }
                                }

                                override fun onFailure(call: Call<Void>, t: Throwable) {
                                    isLoading = false
                                    speakText(tts, "An error occurred. Please check your internet connection.")
                                }
                            })
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    isLoading = false
                    speakText(tts, "An error occurred. Please try again.")
                }
            })
        } else {
            speakText(tts, "User not signed in. Please try again.")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { // Detect double tap anywhere on the screen
                        lifecycleOwner.lifecycleScope.launch {
                            isLoading = true
                            speakText(tts, "Signing in with Google. Please wait.")

                            val success = googleAuthClient.signIn()
                            if (success) {
                                speakText(tts, "Sign-in successful!")
                                isSignedIn = true
                                registerUser()
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
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(52.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else if (isSignedIn) {
                navController.navigate(Routes.ContactFormScreen)
            } else {
                Text(
                    text = "Double-tap anywhere to sign in",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = Color.Black
                )
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




//@Composable
//fun GoogleSignInScreen(
//    googleAuthClient: GoogleAuthClient,
//    lifecycleOwner: LifecycleOwner,
//    navController: NavController
//) {
//    val context = LocalContext.current
//    var isSignIn by remember { mutableStateOf(googleAuthClient.isSingedIn()) }
//    val userId by remember { mutableStateOf(googleAuthClient.getUserId()) }
//    val userName by remember { mutableStateOf(googleAuthClient.getUserName() ?: "Unknown") }
//    val userEmail by remember { mutableStateOf(googleAuthClient.getUserEmail() ?: "No Email") }
//
//
//    @Composable
//    fun submitUser() {
//        val firebaseUser = FirebaseAuth.getInstance().currentUser
//
////        val newUser = UserRequest(
////            firebaseuid = userId,
////            username = userName,
////            email = userEmail,
////        )
//
//        if (firebaseUser != null) {
//            val newUser = UserRequest(
//                firebaseuid = firebaseUser.uid,
//                username = firebaseUser.displayName ?: "Unknown",
//                email = firebaseUser.email ?: "No Email",
//            )
//            // Save user to Firebase Realtime DB
//            val database = FirebaseDatabase.getInstance()
//            val myRef = database.getReference("users").child(firebaseUser.uid)
//            myRef.setValue(newUser)
//
//            //save to Backend API
//            UserApiClient.api.createUser(newUser).enqueue(object : retrofit2.Callback<Void> {
//                override fun onResponse(call: Call<Void>, response: retrofit2.Response<Void>) {
//                    if (response.isSuccessful) {
//                        Toast.makeText(context, "Contact saved!", Toast.LENGTH_SHORT).show()
//                        navController.navigate(Routes.SecondaryContactForm)
//                    } else {
//                        val errorBody = response.errorBody()?.string()
//                        Log.e("API Error", "Code: ${response.code()}, Body: $errorBody")
//                        Toast.makeText(
//                            context,
//                            "Failed to save user: $errorBody",
//                            Toast.LENGTH_LONG
//                        ).show()
//                    }
//                }
//
//                override fun onFailure(call: Call<Void>, t: Throwable) {
//                    Toast.makeText(context, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
//                }
//            })
//        }else{
//            Log.e("Firebase", "User not logged in")
//            Toast.makeText(context, "User is not signed in", Toast.LENGTH_SHORT).show()
//        }
//
//
//    }
//
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .background(Color.White)
//            .padding(16.dp),
//            //.padding(top = 28.dp),
//        horizontalAlignment = Alignment.CenterHorizontally
//    ) {
//        Image(
//            painter = painterResource(id = R.drawable.img),
//            contentDescription = "App Logo",
//            modifier = Modifier
////                .width(401.dp)
////                .height(448.dp)
//                .fillMaxWidth()
//                .aspectRatio(1f)
//        )
//
//        Spacer(modifier = Modifier.height(12.dp))
//
//        Text(
//            text = "Welcome to TembeaNami",
//            color = Color.Black,
//            fontSize = 24.sp,
//            fontWeight = FontWeight.Bold,
//            textAlign = TextAlign.Center,
//            modifier = Modifier.weight(1f) // Allow to grow
//        )
//
//        Spacer(modifier = Modifier.height(12.dp))
//
//        //Subtitle
//
//        Text(
//            text = "Your navigation assistant!",
//            color = Color.Gray,
//            fontSize = 18.sp,
//            textAlign = TextAlign.Center,
//            modifier = Modifier.weight(1f)
//        )
//
//        Spacer(modifier = Modifier.height(52.dp))
//
//        //Sign In Button
//
//        Box(
//            modifier = Modifier
//                .fillMaxWidth()
//                .weight(1f)
//                .background(MaterialTheme.colorScheme.background),
//            contentAlignment = Alignment.Center
//        ) {
//            if (isSignIn) {
//                navController.navigate(Routes.ContactFormScreen)
//            } else {
//                OutlinedButton(onClick = {
//                    lifecycleOwner.lifecycleScope.launch {
//                        isSignIn = googleAuthClient.signIn()
//
//                        submitUser()
//                    }
//                }) {
//                    Text(
//                        text = "Sign In With Google",
//                        fontSize = 16.sp,
//                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
//                    )
//                }
//            }
//        }
//    }
//}

