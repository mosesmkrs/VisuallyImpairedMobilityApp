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
import android.R.id.message
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
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
import androidx.lifecycle.lifecycleScope
import com.example.newapp.R
import com.example.newapp.Routes
import com.example.newapp.SQL.PC.pCViewModel
import com.example.newapp.SQL.SC.sCViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.jvm.java

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


    LaunchedEffect(Unit) {
        val message = "You are on the Home Screen. " +
                "Single tap for SOS Emergency."+
                "Double tap for Start Navigation."

        textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)

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
            Toast.makeText(context, "SMS permission denied", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit){
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }




    // Detect gestures (Swipe Right to go back)
    val gestureDetector = remember {
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 != null && e2 != null && e2.x > e1.x + 100) {
                    navController.navigate(Routes.homeScreen)
                    textToSpeech.speak("Going back", TextToSpeech.QUEUE_FLUSH, null, null)
                    return true
                }
                return false
            }
        })
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        textToSpeech.speak("Starting navigation", TextToSpeech.QUEUE_FLUSH, null, null)
                        navController.navigate(Routes.navigationPage) // Replace with your actual navigation route
                    },
                    onDoubleTap = {
                        textToSpeech.speak("Opening SOS Emergency", TextToSpeech.QUEUE_FLUSH, null, null)
                        //navController.navigate(Routes.ContactFormScreen)
                        lifecycleOwner.lifecycleScope.launch {
                            sendSOSCall(context, userId, pCViewModel, sCViewModel)
                        }
                    }
                )
            }
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
                    sendSOSCall(context, userId, pCViewModel, sCViewModel)
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
        Triple("Matatu Routes", R.drawable.bus_icon, Routes.MatatuPage),
//        Triple("Object Recognition", R.drawable.recog_icon, Routes.navigationPage),
        Triple("Offline Maps", R.drawable.map_icon, Routes.offlineMapPage)
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
                        text = if (isGpsEnabled) "GPS Connected" else "GPS is OFF",
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

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.smartphone_icon),
                        contentDescription = "Haptic Icon",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Haptic Feedback On", fontSize = 16.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Nearby Alerts Section
        Text(
            text = "Nearby Alerts",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Alert Cards
        val alerts = listOf(
            Pair("Crosswalk ahead - 20m", R.drawable.cross_icon),
            Pair("Construction work - 50m", R.drawable.construction_icon)
        )

        alerts.forEach { (text, iconRes) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = iconRes),
                        contentDescription = text,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text, fontSize = 16.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
@SuppressLint("MissingPermission")
suspend fun sendSOSCall(
    context: Context,
    userId: Int,
    pCViewModel: pCViewModel,
    sCViewModel: sCViewModel
) {
    try {
        // Initialize TTS
        val tts = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Log.e("TTS", "Initialization failed")
            }
        }

        // Fetch contacts
        val primaryContact = withContext(Dispatchers.IO) {
            pCViewModel.getPrimaryContact(userId)
        }
        val secondaryContact = withContext(Dispatchers.IO) {
            sCViewModel.getSecondaryContact(userId)
        }

        val firstNumber = primaryContact?.contactnumber
        val secondNumber = secondaryContact?.contactnumber

        if (firstNumber.isNullOrEmpty()) {
            Log.e("SOS", "No primary contact found.")
            return
        }

        // Make the first call
        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$firstNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(callIntent)

        // Wait a bit before asking
        delay(15_000)

        // Speak the prompt
        val message = "Did $firstNumber answer the call? Say yes or no."
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)

        // Start speech recognizer
        withContext(Dispatchers.Main) {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle) {
                    val spoken = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase(Locale.ROOT)
                    if (spoken?.contains("no") == true && !secondNumber.isNullOrEmpty()) {
                        val secondIntent = Intent(Intent.ACTION_CALL).apply {
                            data = Uri.parse("tel:$secondNumber")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(secondIntent)
                    }
                }

                override fun onError(error: Int) {
                    // Fall back to dialog if voice fails
                    showConfirmationDialog(context, tts, firstNumber, secondNumber)
                }

                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(intent)

            // Fallback in case user doesn’t say anything
            delay(10_000)
            recognizer.stopListening()
            recognizer.destroy()
        }

    } catch (e: Exception) {
        Log.e("SOS", "Error during SOS call: ${e.message}")
    }
}

// Fallback dialog if voice input fails
private fun showConfirmationDialog(
    context: Context,
    tts: TextToSpeech,
    firstNumber: String,
    secondNumber: String?
) {
    val message = "Did $firstNumber answer the call? Say yes or no."
    tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)

    AlertDialog.Builder(context)
        .setTitle("Call Check")
        .setMessage(message)
        .setPositiveButton("Yes") { _, _ -> }
        .setNegativeButton("No") { _, _ ->
            secondNumber?.let {
                val secondIntent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$it")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(secondIntent)
            }
        }
        .setCancelable(false)
        .show()
}

//@SuppressLint("MissingPermission")
//suspend fun sendSOSCall(
//    context: Context,
//    userId: Int,
//    pCViewModel: pCViewModel,
//    sCViewModel: sCViewModel
//) {
//    val contacts = listOf("0793472815", "0707471132")
//
//    if (contacts.isEmpty()) {
//        Log.e("SOS", "No contacts found.")
//        return
//    }
//
//    try {
//        val firstNumber = contacts[0]
//        val secondNumber = contacts.getOrNull(1)
//
//        // Call first number
//        val callIntent = Intent(Intent.ACTION_CALL).apply {
//            data = Uri.parse("tel:$firstNumber")
//            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//        }
//        context.startActivity(callIntent)
//
//        // Wait a bit to allow the user to return from the call
//        delay(15_000) // optional wait time
//
//        // Ask the user: did they answer?
//        withContext(Dispatchers.Main) {
//            AlertDialog.Builder(context)
//                .setTitle("Call Check")
//                .setMessage("Did $firstNumber answer the call?")
//                .setPositiveButton("Yes") { _, _ ->
//                    // Do nothing – call was successful
//                }
//                .setNegativeButton("No") { _, _ ->
//                    // Make second call
//                    secondNumber?.let { number ->
//                        val secondIntent = Intent(Intent.ACTION_CALL).apply {
//                            data = Uri.parse("tel:$number")
//                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//                        }
//                        context.startActivity(secondIntent)
//                    }
//                }
//                .setCancelable(false)
//                .show()
//        }
//
//    } catch (e: Exception) {
//        Log.e("SOS", "Error during SOS call: ${e.message}")
//    }
//}

