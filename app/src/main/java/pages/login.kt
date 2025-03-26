package pages


import APIs.GoogleAuthClient
import APIs.UserApiClient
import APIs.UserRequest
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
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import com.example.newapp.R
import com.example.newapp.Routes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.LocalDateTime
import java.util.Locale

@Composable
fun GoogleSignInScreen(
    googleAuthClient: GoogleAuthClient,
    lifecycleOwner: LifecycleOwner,
    navController: NavController,
    tts: TextToSpeech // Receive TTS instance
) {
    val context = LocalContext.current
    var isSignIn by remember { mutableStateOf(googleAuthClient.isSingedIn()) }
    val userId by remember { mutableStateOf(googleAuthClient.getUserId()) }
    val userName by remember { mutableStateOf(googleAuthClient.getUserName() ?: "Unknown") }
    val userEmail by remember { mutableStateOf(googleAuthClient.getUserEmail() ?: "No Email") }
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
            speakText(tts, "Welcome to the app. Your Navigation Assistant! Double tap to sign in")
        }
    }




    fun submitUser() {
        val userRequest = UserRequest(1,"ugfdhjx","cgxkhGL","gmail.com", LocalDateTime.now())

        val call = UserApiClient.api.createUser(userRequest)
        isLoading = true

        call.enqueue(object: Callback<ResponseBody>{
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                isLoading = false
                if (response.isSuccessful) {
                    Log.d("API_SUCCESS","Resonse: ${response.body()?.string()}")
                    speakText(tts, "Account created successfully!")
                    navController.navigate(Routes.ContactFormScreen)
                } else {
                    // Log error response
                    Log.e("API_ERROR", "Error Code: ${response.code()}")
                    Log.e("API_ERROR", "Error Body: ${response.errorBody()?.string()}")
                    speakText(tts, "Failed to register. Please try again.")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                isLoading = false
                Log.e("API_FAILURE","Request Failed: ${t.message}",t)
                speakText(tts, "An error occurred. Please check your internet connection.")
            }
        })
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
                                speakText(tts, "Sign-in successful! Welcome, $userName")
                                isSignIn = true
                                submitUser()
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
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else if (isSignIn) {
                navController.navigate(Routes.ContactFormScreen)
                submitUser()
            } else {
                OutlinedButton(onClick ={
                    lifecycleOwner.lifecycleScope.launch {
                        isSignIn = googleAuthClient.signIn()
                        submitUser()
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






