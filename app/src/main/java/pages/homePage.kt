package pages

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import android.content.Context
import android.media.AudioManager
import android.location.LocationManager
import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import components.Footer
import apis.GoogleAuthClient
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.example.newapp.R
import com.example.newapp.Routes
import com.example.newapp.SQL.PC.pCViewModel
import com.example.newapp.SQL.SC.sCViewModel
import com.example.newapp.SQL.users.UserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.jvm.java
import android.os.Build
import android.view.View
import android.view.KeyEvent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    googleAuthClient: GoogleAuthClient,
    lifecycleOwner: LifecycleOwner,
    navController: NavController,
    textToSpeech: TextToSpeech,
    userId: Int
) {

    val userPhoto by remember { mutableStateOf(googleAuthClient.getUserPhotoUrl()) }
    val context = LocalContext.current
    var isGpsEnabled by remember { mutableStateOf(false) }
    var ringerStatus by remember { mutableStateOf("Checking...") }
    var actualUserId by remember { mutableStateOf(userId) }
    
    // Get the Firebase UID from GoogleAuthClient
    val firebaseUid = googleAuthClient.getUserId()

    // Create UserViewModel to get the correct userId from the database
    val userViewModel: UserViewModel = ViewModelProvider(
        lifecycleOwner as ViewModelStoreOwner,
        ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application)
    ).get(UserViewModel::class.java)

    // Effect to retrieve the userId when Firebase UID is available
    LaunchedEffect(firebaseUid) {
        if (!firebaseUid.isNullOrEmpty()) {
            try {
                // Get the userId from the database using the Firebase UID
                val retrievedUserId = userViewModel.getUserIDByFirebaseUUID(firebaseUid)
                if (retrievedUserId > 0) {
                    actualUserId = retrievedUserId
                    Log.d("HomeScreen", "Retrieved userId: $actualUserId for Firebase UID: $firebaseUid")
                } else {
                    Log.e("HomeScreen", "Invalid userId retrieved: $retrievedUserId")
                }
            } catch (e: Exception) {
                Log.e("HomeScreen", "Error retrieving userId: ${e.message}")
                e.printStackTrace()
            }
        } else {
            Log.e("HomeScreen", "Firebase UID is null or empty")
        }
    }

    // Create a listener for TTS completion events
    val ttsListener = remember {
        object : TextToSpeech.OnInitListener {
            override fun onInit(status: Int) {
                if (status == TextToSpeech.SUCCESS) {
                    // Configure TTS settings
                    textToSpeech.language = Locale.US
                    textToSpeech.setSpeechRate(0.9f)
                    
                    // Speak the welcome message
                    val message = "You are on the Home Screen"
                    
                    // Use a HashMap for utterance parameters
                    val params = HashMap<String, String>()
                    params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "HOME_INTRO"
                    
                    textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, params)
                    Log.d("Homescreen", "TTS initialized and speaking welcome message")
                } else {
                    Log.e("HomeScreen", "Failed to initialize TTS in HomeScreen")
                }
            }
        }
    }
    
    // Use LaunchedEffect to ensure the welcome message is spoken when navigating to this screen
    LaunchedEffect(Unit) {
        // Force re-initialization of TTS when entering this screen
        textToSpeech.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d("HomeScreen", "Started speaking: $utteranceId")
            }
            
            override fun onDone(utteranceId: String?) {
                Log.d("HomeScreen", "Finished speaking: $utteranceId")
            }
            
            override fun onError(utteranceId: String?) {
                Log.e("HomeScreen", "Error speaking: $utteranceId")
            }
        })
        
        // Speak with a slight delay to ensure UI is ready
        delay(300)
        val message = "You are on the Home Screen"
        // Speak directly with utterance ID
        if (textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "HOME_SCREEN_INTRO") == TextToSpeech.ERROR) {
            Log.e("HomeScreen", "Error speaking welcome message")
        } else {
            Log.d("HomeScreen", "Welcome message queued successfully")
        }
    }
    
    // Cleanup when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            if (textToSpeech.isSpeaking) {
                textToSpeech.stop()
            }
        }
    }

    // Initialize ViewModels
    val pCViewModel: pCViewModel = ViewModelProvider(
        lifecycleOwner as ViewModelStoreOwner,
        ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application)
    ).get(pCViewModel::class.java)

    val sCViewModel: sCViewModel = ViewModelProvider(
        lifecycleOwner as ViewModelStoreOwner,
        ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application)
    ).get(sCViewModel::class.java)
    //SMS
    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Call permission denied", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit){
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isGpsEnabled = checkGpsStatus(context)
        }
    }


    fun checkRingerMode(context: Context): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "Audio is OFF"
            AudioManager.RINGER_MODE_VIBRATE -> "Audio is OFF"
            AudioManager.RINGER_MODE_NORMAL -> "Audio Connected"
            else -> "Unknown"
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            isGpsEnabled = checkGpsStatus(context)
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        ringerStatus = checkRingerMode(context)
    }

    // Create ViewModels for emergency contacts
    val primaryContactViewModel = ViewModelProvider(
        lifecycleOwner as ViewModelStoreOwner,
        ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application)
    ).get(pCViewModel::class.java)
    
    val secondaryContactViewModel = ViewModelProvider(
        lifecycleOwner as ViewModelStoreOwner,
        ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as Application)
    ).get(sCViewModel::class.java)



    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    )
    {
        // Top Bar with Home Title and Profile Icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Home",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.weight(2f),
                textAlign = TextAlign.Center
            )
            if (userPhoto != null) {
                AsyncImage(
                    model = userPhoto,
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { navController.navigate(Routes.profilePage) },
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.person_icon),
                    contentDescription = "Default Profile Picture",
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { navController.navigate(Routes.profilePage) },
                    contentScale = ContentScale.Fit
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SOS Emergency Button
        Button(
            onClick = {
                lifecycleOwner.lifecycleScope.launch{
                    sendSOSCall(context, actualUserId, pCViewModel, sCViewModel, textToSpeech, googleAuthClient)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Image(
                painter = painterResource(id = R.drawable.emergency_icon),
                contentDescription = "Alert Icon",
                modifier = Modifier.size(42.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "SOS EMERGENCY", color = Color.White, fontSize = 22.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Navigation Options
        NavigationOptionsGrid(navController)

        Spacer(modifier = Modifier.height(16.dp))

        // Current Status and Alerts
        StatusAndAlertsUI(isGpsEnabled, ringerStatus)

        Spacer(modifier = Modifier.weight(1f))
        Footer(navController)
    }
}

fun checkGpsStatus(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
}

@Composable
fun NavigationOptionsGrid(navController: NavController) {
    val options = listOf(
        Triple("Start Navigation", R.drawable.navigate_icon, Routes.navigationPage),
        Triple("Find Matatu Routes", R.drawable.bus_icon, Routes.MatatuPage),
//        Triple("Object Recognition", R.drawable.recog_icon, Routes.navigationPage),
  //      Triple("Offline Maps", R.drawable.map_icon, Routes.offlineMapPage)
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        items(options) { (label, iconRes, path) ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.5f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF5F5F5))
                    .clickable { navController.navigate(path) }
                    .padding(1.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = iconRes),
                        contentDescription = label,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = label,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
fun StatusAndAlertsUI(isGpsEnabled: Boolean, ringerStatus: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .padding(horizontal = 20.dp)
    ) {
        // Current Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)) // Changed to requested color
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Current Status",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.wifi_icon),
                        contentDescription = "GPS Icon",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isGpsEnabled) "Your Phone is connected to GPS" else "Your phone GPS is OFF",
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.audio_icon),
                        contentDescription = "Audio Icon",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(ringerStatus, fontSize = 16.sp)
                }
            }
        }
    }
}


@SuppressLint("MissingPermission")
suspend fun sendSOSCall(
    context: Context,
    userId: Int,
    pCViewModel: pCViewModel,
    sCViewModel: sCViewModel,
    textToSpeech: TextToSpeech? = null,
    googleAuthClient: GoogleAuthClient? = null
) {
    try {
        // Show feedback to user
        Toast.makeText(context, "SOS Emergency activated", Toast.LENGTH_LONG).show()
        
         //Initialize TTS
        val tts = if (textToSpeech != null) {
            // Use the provided TextToSpeech instance
            textToSpeech
        } else {
            // Create a new TextToSpeech instance if none was provided
            TextToSpeech(context) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    Log.e("TTS", "Initialization failed")
                }
            }.apply {
                language = Locale.getDefault()
            }
        }


        
        // Wait for TTS to initialize
        delay(500)
        
        // Speak the SOS activation message
        tts?.speak("SOS Emergency activated. Contacting emergency contacts now.", TextToSpeech.QUEUE_FLUSH, null, null)
        
        // Check call permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            Log.e("SOS", "Call permission not granted")
            tts?.speak("Call permission not granted. Please enable call permissions in your settings.", TextToSpeech.QUEUE_FLUSH, null, null)
            return
        }

        Log.d("SOS", "Attempting to fetch contacts for userId: $userId")
        
        // Debug the userId
        if (userId <= 0) {
            Log.e("SOS", "Invalid userId: $userId")
            tts?.speak("User ID is invalid. Please sign in again.", TextToSpeech.QUEUE_FLUSH, null, null)
            return
        }
        
        // Try to get the Firebase UID from GoogleAuthClient if available
        val firebaseUid = googleAuthClient?.getUserId()
        Log.d("SOS", "Firebase UID: $firebaseUid")
        
        // Get contacts using direct database queries for more reliability
        val primaryContact = withContext(Dispatchers.IO) {
            try {
                val contact = pCViewModel.getPrimaryContact(userId)
                Log.d("SOS", "Primary contact retrieved: ${contact?.contactnumber ?: "null"}")
                contact
            } catch (e: Exception) {
                Log.e("SOS", "Error fetching primary contact: ${e.message}")
                e.printStackTrace()
                null
            }
        }
        
        val secondaryContact = withContext(Dispatchers.IO) {
            try {
                val contact = sCViewModel.getSecondaryContact(userId)
                Log.d("SOS", "Secondary contact retrieved: ${contact?.contactnumber ?: "null"}")
                contact
            } catch (e: Exception) {
                Log.e("SOS", "Error fetching secondary contact: ${e.message}")
                e.printStackTrace()
                null
            }
        }

        val firstNumber = primaryContact?.contactnumber
        val secondNumber = secondaryContact?.contactnumber

        if (firstNumber.isNullOrEmpty() && secondNumber.isNullOrEmpty()) {
            Log.e("SOS", "No emergency contacts found")
            tts?.speak("No emergency contacts found. Please add emergency contacts in your profile.", TextToSpeech.QUEUE_FLUSH, null, null)
            return
        }

        // Log contacts for debugging
        Log.d("SOS", "Primary contact: $firstNumber")
        Log.d("SOS", "Secondary contact: $secondNumber")
        
        // Try primary contact first if available
        if (!firstNumber.isNullOrEmpty()) {
            val message = "Calling primary emergency contact."
            //provideAudioFeedback(tts,message,false,context)
           tts?.speak("Calling primary emergency contact.", TextToSpeech.QUEUE_FLUSH, null, null)
            delay(1000) // Wait for TTS to complete
            
            try {
                val callIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$firstNumber")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(callIntent)
                Log.d("SOS", "Call initiated to primary contact: $firstNumber")
                
                // Wait for a reasonable time for the call to connect and potentially complete
                delay(30000)
                
                // After the delay, try the secondary contact if available
                if (!secondNumber.isNullOrEmpty()) {
                    val message = "Calling secondary emergency contact"
                    //provideAudioFeedback(tts,message,false,context)
                   tts?.speak("Calling secondary emergency contact.", TextToSpeech.QUEUE_FLUSH, null, null)
                    delay(1000) // Wait for TTS to complete
                    
                    val secondIntent = Intent(Intent.ACTION_CALL).apply {
                        data = Uri.parse("tel:$secondNumber")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(secondIntent)
                    Log.d("SOS", "Call initiated to secondary contact: $secondNumber")
                }
            } catch (e: Exception) {
                Log.e("SOS", "Error making call to primary contact: ${e.message}")
                e.printStackTrace()
                
                // If primary call fails, try secondary
                if (!secondNumber.isNullOrEmpty()) {
                    val message = "Failed to call primary contact, Calling secondary emergency contact"
                    //provideAudioFeedback(tts,message,false,context)
                   tts?.speak("Failed to call primary contact. Calling secondary emergency contact.", TextToSpeech.QUEUE_FLUSH, null, null)
                    delay(1000) // Wait for TTS to complete
                    
                    try {
                        val secondIntent = Intent(Intent.ACTION_CALL).apply {
                            data = Uri.parse("tel:$secondNumber")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(secondIntent)
                        Log.d("SOS", "Call initiated to secondary contact: $secondNumber")
                    } catch (e: Exception) {
                        Log.e("SOS", "Error making call to secondary contact: ${e.message}")
                        e.printStackTrace()
                        val message = "Failed to call emergency contacts, Please try again or call emergency services directly."
                       // provideAudioFeedback(tts,message,false,context)
                       tts?.speak("Failed to call emergency contacts, Please try again or call emergency services directly.", TextToSpeech.QUEUE_FLUSH, null, null)
                    }
                }
            }
        } else if (!secondNumber.isNullOrEmpty()) {
            // If no primary contact but secondary is available
            val message = "No primary contact found, calling secondary emergency contact"
            //provideAudioFeedback(tts,message,false,context)
            tts?.speak("No primary contact found. Calling secondary emergency contact.", TextToSpeech.QUEUE_FLUSH, null, null)
            delay(1000) // Wait for TTS to complete
            
            try {
                val secondIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$secondNumber")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(secondIntent)
                Log.d("SOS", "Call initiated to secondary contact: $secondNumber")
            } catch (e: Exception) {
                Log.e("SOS", "Error making call to secondary contact: ${e.message}")
                e.printStackTrace()
                val message = "Failed to call emergency contacts, please try again or call emergency services directly"
               // provideAudioFeedback(tts,message,false,context)
                tts?.speak("Failed to call emergency contacts, Please try again or call emergency services directly.", TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    } catch (e: Exception) {
        Log.e("SOS", "Error during SOS call: ${e.message}")
        e.printStackTrace()
        Toast.makeText(context, "Error during SOS: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
